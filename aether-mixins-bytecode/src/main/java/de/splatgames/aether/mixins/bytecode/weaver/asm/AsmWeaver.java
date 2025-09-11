package de.splatgames.aether.mixins.bytecode.weaver.asm;

import de.splatgames.aether.mixins.bytecode.weaver.asm.adapter.InjectHeadAdapter;
import de.splatgames.aether.mixins.bytecode.weaver.asm.adapter.InjectTailAdapter;
import de.splatgames.aether.mixins.bytecode.weaver.asm.adapter.RedirectAdapter;
import de.splatgames.aether.mixins.bytecode.weaver.asm.spec.InjectionSpec;
import de.splatgames.aether.mixins.bytecode.weaver.asm.spec.RedirectSpec;
import de.splatgames.aether.mixins.bytecode.weaver.asm.util.ChangeFlag;
import de.splatgames.aether.mixins.bytecode.weaver.hook.HookResolver;
import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.runtime.VerifyFrames;
import de.splatgames.aether.mixins.core.plan.PlannedEntry;
import de.splatgames.aether.mixins.core.plan.PlannedMixin;
import de.splatgames.aether.mixins.core.plan.WeavePlan;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveRequest;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveResult;
import de.splatgames.aether.mixins.core.weaver.spi.Weaver;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * ASM-backed implementation of {@link Weaver} that applies a {@link WeavePlan}
 * to target classes by injecting hooks at {@link Inject.At#HEAD}/{@link Inject.At#TAIL}
 * and redirecting specific call sites to static hook methods.
 *
 * <h2>Feature set (MVP)</h2>
 * <ul>
 *   <li><b>Inject</b>:
 *     <ul>
 *       <li>Join points: {@link Inject.At#HEAD} and {@link Inject.At#TAIL}.</li>
 *       <li>Hook signature must be {@code static} and {@code ()V}.</li>
 *     </ul>
 *   </li>
 *   <li><b>Redirect</b>:
 *     <ul>
 *       <li>Rewrites a single call site (owner/name/desc/{@code kind}/ordinal) to {@code INVOKESTATIC} hook.</li>
 *       <li>Descriptor compatibility is validated upstream; for instance calls, receiver is prepended.</li>
 *     </ul>
 *   </li>
 *   <li><b>Verification</b>:
 *     <ul>
 *       <li>{@link VerifyFrames#NONE}: no recomputation.</li>
 *       <li>{@link VerifyFrames#BASIC}/{@link VerifyFrames#STRICT}: recompute frames and maxs
 *           via {@link ClassWriter#COMPUTE_FRAMES} | {@link ClassWriter#COMPUTE_MAXS}.</li>
 *     </ul>
 *   </li>
 *   <li><b>Ordering</b>:
 *     <ul>
 *       <li>Deterministic ordering for multiple injections/redirects per target method:
 *           redirects → TAIL-injects (by priority asc, then id) → HEAD-injects (by priority asc, then id).</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Processing model</h2>
 * <ul>
 *   <li>All {@link PlannedMixin} entries are resolved to concrete hooks using {@link HookResolver}.</li>
 *   <li>Entries are grouped by target class and then by method signature (name+descriptor).</li>
 *   <li>Each target class is visited at most once; failures are collected in {@link ConfigProblems}.</li>
 *   <li>In safe mode, per-class errors are recorded and original bytes preserved; otherwise errors may propagate.</li>
 * </ul>
 *
 * <p>Thread-safety: instances are not thread-safe; a single instance is expected per weaving run.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class AsmWeaver implements Weaver {

    /**
     * Resolves planned entries to concrete hook symbols (owner/name/desc).
     */
    @NotNull
    private final HookResolver resolver;

    /**
     * Creates a new ASM weaver.
     *
     * @param resolver hook resolver that maps planned entries to concrete hook method symbols; must not be {@code null}
     * @throws NullPointerException if {@code resolver} is {@code null}
     */
    public AsmWeaver(@NotNull final HookResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Executes weaving for the given request and returns a {@link WeaveResult} summary.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Resolves and groups work per target class.</li>
     *   <li>Reads original class bytes from {@link WeaveRequest#source()}.</li>
     *   <li>Transforms classes using ASM visitors; writes transformed bytes to {@link WeaveRequest#sink()} if changed.</li>
     *   <li>Handles failures per safe-mode setting; aggregates outcomes and duration.</li>
     * </ul>
     *
     * @param request  describes plan, I/O endpoints, and runtime options; never {@code null}
     * @param problems diagnostics collector that receives warnings and errors; never {@code null}
     * @return a non-null {@link WeaveResult} summarizing transformed/skipped/failed counts and elapsed time
     * @throws Exception fatal errors (e.g., I/O) if not running in safe mode
     */
    @NotNull
    @Override
    public WeaveResult weave(@NotNull final WeaveRequest request,
                                      @NotNull final ConfigProblems problems) throws Exception {
        final long t0 = System.nanoTime();
        final WeavePlan plan = request.plan();
        final boolean safe = request.runtime().isSafeMode();

        final Map<String, ClassWork> work = this.buildWork(plan, problems);

        final List<WeaveResult.Entry> entries = new ArrayList<>(work.size());
        int transformed = 0, skipped = 0, failed = 0;

        for (final Map.Entry<String, ClassWork> it : work.entrySet()) {
            final String internalName = it.getKey();
            final ClassWork cw = it.getValue();

            final byte[] original;
            try {
                original = request.source().getClassBytes(internalName);
            } catch (final IOException ioe) {
                problems.error("weave/" + internalName, "Failed reading class: " + ioe.getMessage());
                if (!safe) throw ioe;
                entries.add(new WeaveResult.Entry(internalName, WeaveResult.Outcome.FAILED));
                failed++;
                continue;
            }

            if (original == null) {
                problems.warn("weave/" + internalName, "Target class not found; skipping.");
                entries.add(new WeaveResult.Entry(internalName, WeaveResult.Outcome.SKIPPED));
                skipped++;
                continue;
            }

            try {
                final byte[] transformedBytes = this.weaveClass(original, internalName, cw, request, problems);
                final boolean changed = transformedBytes != null;

                if (changed) {
                    request.sink().accept(internalName, transformedBytes);
                    entries.add(new WeaveResult.Entry(internalName, WeaveResult.Outcome.TRANSFORMED));
                    transformed++;
                } else {
                    entries.add(new WeaveResult.Entry(internalName, WeaveResult.Outcome.SKIPPED));
                    skipped++;
                }
            } catch (final Throwable t) {
                problems.error("weave/" + internalName, "Weaving failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                if (!safe) throw t;
                entries.add(new WeaveResult.Entry(internalName, WeaveResult.Outcome.FAILED));
                failed++;
            }
        }

        final long duration = System.nanoTime() - t0;
        return WeaveResult.of(entries, transformed, skipped, failed, duration);
    }

    /**
     * Builds the per-class work set by resolving hooks for every {@link PlannedEntry} in the {@link WeavePlan}.
     *
     * <p>For each {@link PlannedMixin}, all target classes are collected; each {@link PlannedEntry}
     * is resolved to a {@link ResolvedHook} via {@link HookResolver} and grouped under the target
     * method signature (name + descriptor). Lists are then sorted deterministically.</p>
     *
     * @param plan     the weave plan to consume; never {@code null}
     * @param problems diagnostics collector for warnings and resolution errors; never {@code null}
     * @return a map from internal class name to {@link ClassWork}, never {@code null}
     */
    @NotNull
    private Map<String, ClassWork> buildWork(@NotNull final WeavePlan plan,
                                                      @NotNull final ConfigProblems problems) {
        final Map<String, ClassWork> map = new LinkedHashMap<>();

        for (final PlannedMixin mixin : plan.getMixins()) {
            for (final String target : mixin.getTargets()) {
                final ClassWork cw = map.computeIfAbsent(target, k -> new ClassWork(target));
                for (final PlannedEntry pe : mixin.getEntries()) {
                    final Optional<ResolvedHook> rhOpt = this.resolver.resolve(
                            mixin, pe, problems, "resolve/" + target + "/" + mixin.getClassName() + ":" + pe.getId());
                    if (rhOpt.isEmpty()) {
                        problems.error("resolve/" + target, "No hook resolved for " + mixin.getClassName() + " id=" + pe.getId());
                        continue;
                    }
                    final ResolvedHook rh = rhOpt.get();
                    switch (pe.getKind()) {
                        case INJECT -> {
                            if (!"()V".equals(rh.desc())) {
                                problems.error("resolve/" + target, "INJECT hook must be ()V in MVP: " + rh.owner() + "." + rh.name() + rh.desc());
                                continue;
                            }
                            cw.getInjects().computeIfAbsent(this.sigOf(pe.getMethod()), k -> new ArrayList<>())
                                    .add(new InjectionSpec(pe.getAt(), rh, pe.isOptional(), pe.isRemap(), pe.getId(), mixin.getPriority()));
                        }
                        case REDIRECT ->
                                cw.getRedirects().computeIfAbsent(this.sigOf(pe.getMethod()), k -> new ArrayList<>())
                                .add(new RedirectSpec(pe.getCallOwner(), pe.getCallName(), pe.getCallDesc(),
                                        pe.getInvokeKind(), pe.getOrdinal(), rh, pe.isOptional(), pe.isRemap(), pe.getId()));
                    }
                }
            }
        }

        // Deterministic ordering for reproducible output
        for (final ClassWork cw : map.values()) {
            cw.getInjects().values().forEach(list -> list.sort(
                    Comparator.comparingInt(InjectionSpec::priority).thenComparing(InjectionSpec::id)
            ));
            cw.getRedirects().values().forEach(list -> list.sort(
                    Comparator.comparing(RedirectSpec::owner)
                            .thenComparing(RedirectSpec::name)
                            .thenComparing(RedirectSpec::desc)
                            .thenComparingInt(RedirectSpec::ordinal)
            ));
        }

        return map;
    }

    /**
     * Transforms a single class according to its {@link ClassWork}.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Selects frame recomputation mode according to {@link VerifyFrames}.</li>
     *   <li>Wraps target methods with {@link RedirectAdapter}, then {@link InjectTailAdapter}, then {@link InjectHeadAdapter}.</li>
     *   <li>Uses {@link ChangeFlag} to record whether any bytecode change occurred.</li>
     *   <li>Returns {@code null} if no change was applied to signal a skipped class.</li>
     * </ul>
     *
     * @param original     original class bytes; never {@code null}
     * @param internalName internal JVM class name (slash-separated); never {@code null}
     * @param work         grouped injection/redirect specs for this class; never {@code null}
     * @param request      current weave request (for runtime options); never {@code null}
     * @param problems     diagnostics collector; never {@code null}
     * @return transformed byte array if any changes were applied; {@code null} if unchanged
     */
    private byte[] weaveClass(@NotNull final byte[] original,
                              @NotNull final String internalName,
                              @NotNull final ClassWork work,
                              @NotNull final WeaveRequest request,
                              @NotNull final ConfigProblems problems) {
        final VerifyFrames verify = request.runtime().getVerifyFrames();
        final boolean compute = verify.isAtLeast(VerifyFrames.BASIC);

        final ClassReader cr = new ClassReader(original);
        final ClassWriter cw = compute
                ? new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
                : new ClassWriter(0);

        final ChangeFlag changed = new ChangeFlag();

        final ClassVisitor cv = new ClassVisitor(ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(final int access,
                                             @NotNull final String name,
                                             @NotNull final String descriptor,
                                             final String signature,
                                             final String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                final String sig = name + descriptor;

                final List<InjectionSpec> inj = work.getInjects().getOrDefault(sig, List.of());
                final List<RedirectSpec> red = work.getRedirects().getOrDefault(sig, List.of());
                if (inj.isEmpty() && red.isEmpty()) {
                    return mv;
                }

                // Redirects first
                for (final RedirectSpec r : red) {
                    mv = new RedirectAdapter(
                            this.api, mv,
                            r.owner(), r.name(), r.desc(), r.kind(), r.ordinal(),
                            r.hook(), r.optional(), r.id(),
                            changed::getAndSet,
                            problems, internalName, sig
                    );
                }

                // Split injects by kind and sort deterministically
                final List<InjectionSpec> head = inj.stream().filter(i -> i.at() == Inject.At.HEAD).toList();
                final List<InjectionSpec> tail = inj.stream().filter(i -> i.at() == Inject.At.TAIL).toList();

                final var headSorted = new ArrayList<>(head);
                headSorted.sort(Comparator
                        .comparingInt(InjectionSpec::priority)
                        .thenComparing(InjectionSpec::id)
                );

                final var tailSorted = new ArrayList<>(tail);
                tailSorted.sort(Comparator
                        .comparingInt(InjectionSpec::priority)
                        .thenComparing(InjectionSpec::id)
                );

                // Then TAIL injects, then HEAD injects
                for (final InjectionSpec t : tailSorted) {
                    mv = new InjectTailAdapter(
                            this.api, mv,
                            t.hook(), t.optional(), t.id(),
                            changed::getAndSet,
                            problems, internalName, sig
                    );
                }
                for (final InjectionSpec h : headSorted) {
                    mv = new InjectHeadAdapter(
                            this.api, mv,
                            h.hook(), h.optional(), h.id(),
                            changed::getAndSet,
                            problems, internalName, sig
                    );
                }
                return mv;
            }
        };

        cr.accept(cv, compute ? ClassReader.SKIP_FRAMES : 0);
        return changed.isSet() ? cw.toByteArray() : null;
    }

    /**
     * Derives the per-method signature key used to group specs within a class.
     *
     * <p>In the current MVP, {@code namePlusDesc} already matches the key format, but this method
     * centralizes potential future normalization (e.g., signature canonicalization).</p>
     *
     * @param namePlusDesc a concatenation of method name and descriptor (e.g., {@code doWork(I)I}); never {@code null}
     * @return the signature key (currently identical to input); never {@code null}
     */
    @NotNull
    private String sigOf(@NotNull final String namePlusDesc) {
        return namePlusDesc;
    }
}
