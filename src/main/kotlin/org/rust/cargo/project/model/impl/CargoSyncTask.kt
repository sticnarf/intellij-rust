/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.cargo.project.model.impl

import com.intellij.build.BuildContentDescriptor
import com.intellij.build.BuildDescriptor
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.containers.orNull
import com.intellij.util.io.exists
import com.intellij.util.text.SemVer
import org.rust.RsTask
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.runconfig.buildtool.isNavigateToErrorWhenFailed
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.cargo.toolchain.tools.cargoOrWrapper
import org.rust.cargo.toolchain.tools.rustc
import org.rust.cargo.toolchain.tools.rustup
import org.rust.cargo.util.DownloadResult
import org.rust.openapiext.TaskResult
import org.rust.stdext.mapNotNullToSet
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent

class CargoSyncTask(
    project: Project,
    private val cargoProjects: List<CargoProjectImpl>,
    private val result: CompletableFuture<List<CargoProjectImpl>>
) : Task.Backgroundable(project, "Reloading Cargo projects", true), RsTask {

    override val taskType: RsTask.TaskType
        get() = RsTask.TaskType.CARGO_SYNC

    override val runSyncInUnitTests: Boolean
        get() = true

    override fun run(indicator: ProgressIndicator) {
        LOG.info("CargoSyncTask started")
        indicator.isIndeterminate = true

        val syncProgress = SyncViewManager.createBuildProgress(project)

        val refreshedProjects = try {
            syncProgress.start(createSyncProgressDescriptor(indicator))
            val refreshedProjects = doRun(indicator, syncProgress)
            val isUpdateFailed = refreshedProjects.any { it.mergedStatus is CargoProject.UpdateStatus.UpdateFailed }
            if (isUpdateFailed) {
                syncProgress.fail()
            } else {
                syncProgress.finish()
            }
            refreshedProjects
        } catch (e: Throwable) {
            if (e is ProcessCanceledException) {
                syncProgress.cancel()
            } else {
                syncProgress.fail()
            }
            result.completeExceptionally(e)
            throw e
        }
        result.complete(refreshedProjects)
    }

    private fun doRun(
        indicator: ProgressIndicator,
        syncProgress: BuildProgress<BuildProgressDescriptor>
    ): List<CargoProjectImpl> {
        val toolchain = project.toolchain

        @Suppress("UnnecessaryVariable")
        val refreshedProjects = if (toolchain == null) {
            syncProgress.fail(System.currentTimeMillis(), "Cargo project update failed:\nNo Rust toolchain")
            cargoProjects
        } else {
            cargoProjects.map { cargoProject ->
                syncProgress.runWithChildProgress(
                    "Sync ${cargoProject.presentableName} project",
                    createContext = { it },
                    action = { childProgress ->
                        if (!cargoProject.workingDirectory.exists()) {
                            val stdlibStatus = CargoProject.UpdateStatus.UpdateFailed("Project directory does not exist")
                            CargoProjectWithStdlib(cargoProject.copy(stdlibStatus = stdlibStatus), null)
                        } else {
                            val context = SyncContext(project, cargoProject, toolchain, indicator, childProgress)
                            val rustcInfoResult = fetchRustcInfo(context)
                            val rustcInfo = (rustcInfoResult as? TaskResult.Ok)?.value
                            val cargoProjectWithRustcInfoAndWorkspace = cargoProject.withRustcInfo(rustcInfoResult)
                                .withWorkspace(fetchCargoWorkspace(context, rustcInfo))
                            CargoProjectWithStdlib(
                                cargoProjectWithRustcInfoAndWorkspace,
                                fetchStdlib(context, cargoProjectWithRustcInfoAndWorkspace, rustcInfo)
                            )
                        }
                    }
                )
            }.chooseAndAttachStdlib()
                .deduplicateProjects()
        }

        return refreshedProjects
    }

    private fun createSyncProgressDescriptor(progress: ProgressIndicator): BuildProgressDescriptor {
        val buildContentDescriptor = BuildContentDescriptor(null, null, object : JComponent() {}, "Cargo")
        buildContentDescriptor.isActivateToolWindowWhenFailed = true
        buildContentDescriptor.isActivateToolWindowWhenAdded = false
        buildContentDescriptor.isNavigateToErrorWhenFailed = project.rustSettings.autoShowErrorsInEditor
        val refreshAction = ActionManager.getInstance().getAction("Cargo.RefreshCargoProject")
        val descriptor = DefaultBuildDescriptor("Cargo", "Cargo", project.basePath!!, System.currentTimeMillis())
            .withContentDescriptor { buildContentDescriptor }
            .withRestartAction(refreshAction)
            .withRestartAction(StopAction(progress))
        return object : BuildProgressDescriptor {
            override fun getTitle(): String = descriptor.title
            override fun getBuildDescriptor(): BuildDescriptor = descriptor
        }
    }

    companion object {
        private val LOG = logger<CargoSyncTask>()
    }

    private class StopAction(private val progress: ProgressIndicator) :
        DumbAwareAction({ "Stop" }, AllIcons.Actions.Suspend) {

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = progress.isRunning
        }

        override fun actionPerformed(e: AnActionEvent) {
            progress.cancel()
        }
    }

    data class SyncContext(
        val project: Project,
        val oldCargoProject: CargoProjectImpl,
        val toolchain: RsToolchainBase,
        val progress: ProgressIndicator,
        val syncProgress: BuildProgress<BuildProgressDescriptor>
    ) {
        fun <T> runWithChildProgress(
            title: String,
            action: (SyncContext) -> TaskResult<T>
        ): TaskResult<T> {
            progress.checkCanceled()
            progress.text = title

            return syncProgress.runWithChildProgress(title, { copy(syncProgress = it) }, action) { childProgress, result ->
                when (result) {
                    is TaskResult.Ok -> childProgress.finish()
                    is TaskResult.Err -> {
                        childProgress.message(result.reason, result.message.orEmpty(), MessageEvent.Kind.ERROR, null)
                        childProgress.fail()
                    }
                }
            }
        }

        fun withProgressText(text: String) {
            progress.text = text
            syncProgress.progress(text)
        }
    }
}

private class CargoProjectWithStdlib(
    val cargoProject: CargoProjectImpl,
    val stdlib: TaskResult<StandardLibrary>?
)

private class CargoProjectWithExistingStdlib(
    val cargoProject: CargoProjectImpl,
    val rustcVersion: RustcVersion,
    val stdlib: StandardLibrary
)

/**
 * Intellij-Rust plugin currently supports only one stdlib at a time. If there are different standard libraries in
 * different cargo projects, select and use the most recent of them.
 * A cargo project may have a different stdlib if there is a `rust-toolchain.toml` file,
 * or if it uses `rustup override`
 */
private fun List<CargoProjectWithStdlib>.chooseAndAttachStdlib(): List<CargoProjectImpl> {
    val projectsWithStdlib = mapNotNull {
        val rustcVersion = it.cargoProject.rustcInfo?.version ?: return@mapNotNull null
        val stdlib = (it.stdlib as? TaskResult.Ok)?.value ?: return@mapNotNull null
        CargoProjectWithExistingStdlib(it.cargoProject, rustcVersion, stdlib)
    }
    val embeddedStdlib = projectsWithStdlib.find { it.stdlib.isPartOfCargoProject }
    val theMostRecentStdlib = embeddedStdlib
        ?: projectsWithStdlib.maxByOrNull { it.rustcVersion.semver }
    return map {
        when {
            it.stdlib == null -> it.cargoProject
            it.stdlib is TaskResult.Err -> it.cargoProject.withStdlib(it.stdlib)
            theMostRecentStdlib != null -> it.cargoProject.withStdlib(TaskResult.Ok(theMostRecentStdlib.stdlib))
            else -> it.cargoProject.withStdlib(it.stdlib)
        }
    }
}

/**
 * There are 2 kinds of possible cargo project duplication:
 *
 * 1. Direct duplication: when there are 2 projects with the same [CargoProject.manifest] value.
 *    Looks like it's impossible to make such duplication without editing workspace.xml.
 * 2. Duplication of workspace members: when there is a project with cargo workspace consist of
 *    several packages and one of them is also added as a separate CargoProject. It's possible to
 *    make such duplication: initially, there should be 2 independent CargoProjects, but then,
 *    one of them should become a workspace member of the other project (a user can do this by
 *    modifying Cargo.toml)
 *
 * See tests in `CargoProjectDeduplicationTest`
 *
 * Keep in sync with [org.rust.cargo.project.model.impl.isExistingProject]
 */
private fun List<CargoProjectImpl>.deduplicateProjects(): List<CargoProjectImpl> {
    val projects = distinctBy { it.manifest }

    // Maps `project root` -> [package root] for workspace packages
    val projectRootToWorkspacePackages: Map<Path, Set<Path>> = projects.associate { project ->
        project.manifest.parent to run {
            val workspace = project.rawWorkspace ?: return@run emptySet()
            workspace.packages.mapNotNullToSet { pkg ->
                if (pkg.origin != PackageOrigin.WORKSPACE) return@mapNotNullToSet null
                pkg.rootDirectory
            }
        }
    }

    val projectsToRemove = mutableSetOf<CargoProjectImpl>()

    // Consider this setup:
    // ```
    // +-- root-project // <- attached as cargo project
    // |   +-- Cargo.toml // workspace.members = ["subpackage"]
    // |   +-- subpackage // <- attached as cargo project (should be removed)
    // |       +-- Cargo.toml
    // ```
    // In this case, we want to remove `subpackage` project and leave only `root-project`.
    // Note that we want to remove it ONLY if `subpackage` is a workspace member of `root-project`.
    // Cargo requires that a workspace root is always an ancestor of its members in file hierarchy,
    // so here we iterate ancestor directories of each cargo project root and removing the project if
    // there is a project that includes this one as a workspace package
    for (project in projects) {
        val projectRootPath = project.manifest.parent
        for (ancestorPath in generateSequence(projectRootPath.parent) { it.parent }) {
            val ancestorProjectPackageRoots = projectRootToWorkspacePackages[ancestorPath]
            if (ancestorProjectPackageRoots != null && projectRootPath in ancestorProjectPackageRoots) {
                projectsToRemove += project
            }
        }
    }

    return projects.filter { it !in projectsToRemove }
}

private fun fetchRustcInfo(context: CargoSyncTask.SyncContext): TaskResult<RustcInfo> {
    return context.runWithChildProgress("Getting toolchain version") { childContext ->
        if (!childContext.toolchain.looksLikeValidToolchain()) {
            return@runWithChildProgress TaskResult.Err("Invalid Rust toolchain ${childContext.toolchain.presentableLocation}")
        }

        val workingDirectory = childContext.oldCargoProject.workingDirectory

        val rustcVersion = childContext.toolchain.rustc().queryVersion(workingDirectory)
        val sysroot = UnitTestRustcCache.cached(rustcVersion) { childContext.toolchain.rustc().getSysroot(workingDirectory) }
            ?: return@runWithChildProgress TaskResult.Err("failed to get project sysroot")
        val rustcTargets = UnitTestRustcCache.cached(rustcVersion) { childContext.toolchain.rustc().getTargets(workingDirectory) }

        TaskResult.Ok(RustcInfo(sysroot, rustcVersion, rustcTargets))
    }
}

private fun fetchCargoWorkspace(context: CargoSyncTask.SyncContext, rustcInfo: RustcInfo?): TaskResult<CargoWorkspace> {
    return context.runWithChildProgress("Updating workspace info") { childContext ->

        val toolchain = childContext.toolchain
        if (!toolchain.looksLikeValidToolchain()) {
            return@runWithChildProgress TaskResult.Err("Invalid Rust toolchain ${toolchain.presentableLocation}")
        }
        val projectDirectory = childContext.oldCargoProject.workingDirectory
        val cargo = toolchain.cargoOrWrapper(projectDirectory)
        try {
            CargoEventService.getInstance(childContext.project).onMetadataCall(projectDirectory)
            val projectDescriptionData = cargo.fullProjectDescription(childContext.project, projectDirectory, object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                    val text = event.text.trim { it <= ' ' }
                    if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                        childContext.withProgressText(text)
                    }

                }
            })
            val manifestPath = projectDirectory.resolve("Cargo.toml")

            val cfgOptions = try {
                 UnitTestRustcCache.cached(rustcInfo?.version, cacheIf = { !projectDirectory.resolve(".cargo").exists() }) {
                     cargo.getCfgOption(childContext.project, projectDirectory)
                 }
            } catch (e: ExecutionException) {
                val rustcVersion = rustcInfo?.version?.semver
                if (rustcVersion == null || rustcVersion > RUST_1_51) {
                    val message = "Fetching target specific `cfg` options failed. Fallback to host options.\n\n${e.message.orEmpty()}"
                    @Suppress("DialogTitleCapitalization")
                    childContext.syncProgress.message(
                        "Fetching target specific `cfg` options",
                        message,
                        MessageEvent.Kind.WARNING,
                        null
                    )
                }
                toolchain.rustc().getCfgOptions(projectDirectory)
            }

            val ws = CargoWorkspace.deserialize(manifestPath, projectDescriptionData, cfgOptions)
            TaskResult.Ok(ws)
        } catch (e: ExecutionException) {
            TaskResult.Err("Failed to run Cargo", e.message)
        }
    }
}

private fun fetchStdlib(context: CargoSyncTask.SyncContext, cargoProject: CargoProjectImpl, rustcInfo: RustcInfo?): TaskResult<StandardLibrary> {
    return context.runWithChildProgress("Getting Rust stdlib") { childContext ->

        val workingDirectory = cargoProject.workingDirectory
        if (cargoProject.doesProjectLooksLikeRustc()) {
            // rust-lang/rust contains stdlib inside the project
            val std = StandardLibrary.fromPath(
                childContext.project,
                workingDirectory.toString(),
                rustcInfo,
                isPartOfCargoProject = true
            )
            if (std != null) {
                return@runWithChildProgress TaskResult.Ok(std)
            }
        }

        val rustup = childContext.toolchain.rustup(workingDirectory)
        if (rustup == null) {
            val explicitPath = childContext.project.rustSettings.explicitPathToStdlib
                ?: childContext.toolchain.rustc().getStdlibFromSysroot(workingDirectory)?.path
            val lib = explicitPath?.let { StandardLibrary.fromPath(childContext.project, it, rustcInfo) }
            return@runWithChildProgress when {
                explicitPath == null -> TaskResult.Err("no explicit stdlib or rustup found")
                lib == null -> TaskResult.Err("invalid standard library: $explicitPath")
                else -> TaskResult.Ok(lib)
            }
        }

        rustup.fetchStdlib(childContext.project, rustcInfo)
    }
}


private fun Rustup.fetchStdlib(project: Project, rustcInfo: RustcInfo?): TaskResult<StandardLibrary> {
    return when (val download = UnitTestRustcCache.cached(rustcInfo?.version) { downloadStdlib() }) {
        is DownloadResult.Ok -> {
            val lib = StandardLibrary.fromFile(project, download.value, rustcInfo)
            if (lib == null) {
                TaskResult.Err("Corrupted standard library: ${download.value.presentableUrl}")
            } else {
                TaskResult.Ok(lib)
            }
        }
        is DownloadResult.Err -> TaskResult.Err("Download failed: ${download.error}")
    }
}

private fun <T, R> BuildProgress<BuildProgressDescriptor>.runWithChildProgress(
    title: String,
    createContext: (BuildProgress<BuildProgressDescriptor>) -> T,
    action: (T) -> R,
    onResult: (BuildProgress<BuildProgressDescriptor>, R) -> Unit = { progress, _ -> progress.finish() }
): R {
    val childProgress = startChildProgress(title)
    try {
        val context = createContext(childProgress)
        val result = action(context)
        onResult(childProgress, result)
        return result
    } catch (e: Throwable) {
        if (e is ProcessCanceledException) {
            cancel()
        } else {
            fail()
        }
        throw e
    }
}

/**
 * This cache is really used *only* in unit tests.
 *
 * We assume that `sysroot`, a `list of targets`, `--print cfg` and `stdlib`
 * are not changed until the version of rustc is changed, so we can cache these values for all tests.
 * The cache significantly speeds up heavy tests with a full toolchain (`RsWithToolchainTestBase`)
 */
private object UnitTestRustcCache {
    private val cache: ConcurrentHashMap<Pair<RustcVersion, Class<*>>, Optional<Any>> = ConcurrentHashMap()

    inline fun <reified T> cached(rustcVersion: RustcVersion?, cacheIf: () -> Boolean = { true }, noinline computation: () -> T): T {
        if (!isUnitTestMode || rustcVersion == null || !cacheIf()) return computation()
        return cachedInner(rustcVersion, T::class.java, computation)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> cachedInner(rustcVersion: RustcVersion, cls: Class<T>, computation: () -> T): T {
        return cache.getOrPut(rustcVersion to cls) { Optional.ofNullable(computation()) }.orNull() as T
    }
}

private val RUST_1_51: SemVer = SemVer.parseFromText("1.51.0")!!
