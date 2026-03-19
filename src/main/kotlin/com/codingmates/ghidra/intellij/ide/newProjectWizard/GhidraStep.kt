package com.codingmates.ghidra.intellij.ide.newProjectWizard

import com.codingmates.ghidra.intellij.ide.GhidraBundle
import com.codingmates.ghidra.intellij.ide.model.isGhidraInstallationPath
import com.codingmates.ghidra.intellij.ide.model.isGhidraSourcesPath
import com.codingmates.ghidra.intellij.ide.newProjectWizard.GhidraData.Companion.ghidraData
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraLauncherConfiguration
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraLauncherConfigurationType
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.ide.projectWizard.projectWizardJdkComboBox
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ui.dsl.builder.whenItemSelectedFromUi
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.openapi.util.Pair
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.layout.ValidationInfoBuilder


class GhidraStep(parent: NewProjectWizardStep) :
    AbstractNewProjectWizardStep(parent), GhidraData {
    // Whether project is a Ghidra script or module
    override val typeProperty = propertyGraph.property(GhidraProjectType.Module)
    override var type: GhidraProjectType by typeProperty
    // Path to Ghidra installation
    override val pathProperty = propertyGraph.property("")
    override var path: String by pathProperty
    // JDK to use
    override val sdkProperty: GraphProperty<Sdk?> = propertyGraph.property(null)
    override var sdk: Sdk? by sdkProperty
    override val sdkDownloadTaskProperty: GraphProperty<SdkDownloadTask?> = propertyGraph.property(null)
    override var sdkDownloadTask: SdkDownloadTask? by sdkDownloadTaskProperty
    // Ghidra modules
    override val ghidraModulesProperty = propertyGraph.property<Map<String, String>>(emptyMap())
    override var ghidraModules: Map<String, String> by ghidraModulesProperty

    init {
        data.putUserData(GhidraData.KEY, this)
        pathProperty.set(GhidraNewProjectWizardState.lastPath)
    }

    override fun setupUI(builder: Panel) {
        with(builder) {
            setupGhidraSettingsUI(this)
            setupJavaSdkUI(this)
        }
    }

    fun setupGhidraSettingsUI(builder: Panel) {
        builder.row(GhidraBundle.message("ghidra.facet.editor.installation")) {
            val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(GhidraBundle.message("ghidra.facet.editor.installation.dialog.title"))
                .withPathToTextConvertor(::getPresentablePath)
                .withTextToPathConvertor(::getCanonicalPath)
            textFieldWithBrowseButton(fileChooserDescriptor, context.project)
                .bindText(pathProperty.toUiPathProperty())
                .align(AlignX.FILL)
                .validationOnInput { validateGhidraPath() }
                .validationOnApply { validateGhidraPath() }
        }
        builder.row("Project Type:") {
            comboBox(GhidraProjectType.entries)
                .bindItem(typeProperty)
        }
    }

    fun setupJavaSdkUI(builder: Panel) {
        builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
            projectWizardJdkComboBox(this, sdkProperty, sdkDownloadTaskProperty)
                .whenItemSelectedFromUi { logSdkChanged(sdk) }
                .onApply { logSdkFinished(sdk) }
        }.bottomGap(BottomGap.SMALL)
    }

    override fun setupProject(project: Project) {
        super.setupProject(project)
        ghidraData?.resolve()
        GhidraNewProjectWizardState.lastPath = path

        // Set up Module
        val builder = JavaModuleBuilder()
        configureModuleBuilder(project, builder)
        val module = setupProjectFromBuilder(project, builder)
        module?.let(::startJdkDownloadIfNeeded)
        ApplicationManager.getApplication().runWriteAction {
            val ghidraLib = createGhidraLibrary(project)
            module?.let { ModuleRootModificationUtil.addDependency(it, ghidraLib) }
        }
        createRunConfigInstance(project)
    }

    private fun configureModuleBuilder(project: Project, builder: JavaModuleBuilder) {
        val basePath = Path(project.basePath ?: error(GhidraBundle.message("ghidra.wizard.path.error")))
        val moduleFileLocation = basePath.toString()
        val moduleName = project.name
        val moduleFile = Paths.get(moduleFileLocation, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION)

        builder.name = moduleName
        builder.moduleFilePath = FileUtil.toSystemDependentName(moduleFile.toString())
        builder.addSourcePath(Pair.create(FileUtil.toSystemDependentName(moduleFileLocation), ""))

        if (context.isCreatingNewProject) {
            // New project with a single module: set project JDK
            context.projectJdk = sdk
        }
        else {
            // New module in an existing project: set module JDK
            val isSameSdk = ProjectRootManager.getInstance(project).projectSdk?.name == sdk?.name
            builder.moduleJdk = if (isSameSdk) null else sdk
        }
    }

    private fun startJdkDownloadIfNeeded(module: Module) {
        val sdkDownloadTask = sdkDownloadTask
        if (sdkDownloadTask is JdkDownloadTask) {
            // Download the SDK on project creation
            module.project.service<JdkDownloadService>().scheduleDownloadJdk(sdkDownloadTask, module, context.isCreatingNewProject)
        }
    }

    private fun createRunConfigInstance(project: Project) {
        // Create run configuration entry for the project
        val runManager = RunManager.getInstance(project)

        val factory = ConfigurationTypeUtil
            .findConfigurationType(GhidraLauncherConfigurationType::class.java)
            .configurationFactories
            .firstOrNull()
            ?: error(GhidraBundle.message("ghidra.wizard.runconfig.type.error"))
        val settings = runManager.createConfiguration(
            "Ghidra",
            factory,
        )
        val configuration = settings.configuration as GhidraLauncherConfiguration
        configuration.apply {
            setGhidraPath(path)
            alternativeJrePath = ProjectRootManager.getInstance(project).projectSdk?.homePath
        }
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
    }

    private fun createGhidraLibrary(
        project: Project
    ): Library {
        val classRoots: List<VirtualFile> = getClassRoots()
        val sourceRoots: List<VirtualFile> = getSourceRoots()
        val registrar = LibraryTablesRegistrar.getInstance()
        val projectLibTable = registrar.getLibraryTable(project)
        val libModel = projectLibTable.modifiableModel
        val ghidraLib = projectLibTable.getLibraryByName("Ghidra")
            ?: libModel.createLibrary("Ghidra")
        libModel.commit()


        ghidraLib.modifiableModel.apply {
            listOf(OrderRootType.CLASSES, OrderRootType.SOURCES).forEach { rootType ->
                getUrls(rootType).forEach { removeRoot(it, rootType) }
            }

            classRoots.forEach { addRoot(it, OrderRootType.CLASSES) }
            sourceRoots.forEach { addRoot(it, OrderRootType.SOURCES) }
        }.commit()
        return ghidraLib
    }

    private fun getSourceRoots(): List<VirtualFile> {
        val vfs = VirtualFileManager.getInstance()
        fun Path.toVfs(): VirtualFile? =
            vfs.refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(this))


        val sourceRoots: List<VirtualFile> =
            ghidraModules.asSequence()
                .map { (moduleName, moduleRootStr) -> Paths.get(moduleRootStr, "lib", "${moduleName}-src.zip") }
                .mapNotNull(Path::toVfs)
                .toList()
        return sourceRoots
    }

    private fun getClassRoots(): List<VirtualFile> {
        val vfs = VirtualFileManager.getInstance()
        fun Path.toVfs(): VirtualFile? =
            vfs.refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(this))

        val classRoots: List<VirtualFile> = ghidraModules
            .values
            .map { Paths.get(it, "lib") }
            .filter(Files::isDirectory)
            .map(Files::list)
            .flatMap { it.use { stream -> stream.toList() } }
            .filter {
                val fileName = it.fileName.toString()
                fileName.endsWith(".jar", ignoreCase = true) && !fileName.endsWith("-src.zip", ignoreCase = true)
            }
            .mapNotNull(Path::toVfs)
        return classRoots
    }

    private fun ValidationInfoBuilder.validateGhidraPath(): ValidationInfo? {
        if (!isGhidraInstallationPath(path)) {
            return error(GhidraBundle.message("ghidra.facet.editor.installation.error.no-properties"))
        }
        if (isGhidraSourcesPath(path)) {
            return error(GhidraBundle.message("ghidra.facet.editor.installation.error.sources"))
        }
        return null
    }
}