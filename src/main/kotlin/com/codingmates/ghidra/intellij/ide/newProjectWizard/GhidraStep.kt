package com.codingmates.ghidra.intellij.ide.newProjectWizard

import com.codingmates.ghidra.intellij.ide.GhidraBundle
import com.codingmates.ghidra.intellij.ide.model.isGhidraInstallationPath
import com.codingmates.ghidra.intellij.ide.model.isGhidraSourcesPath
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraLauncherConfiguration
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraLauncherConfigurationType
import com.codingmates.ghidra.intellij.ide.settings.GhidraSettings
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.ide.projectWizard.projectWizardJdkComboBox
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.setupProjectFromBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.util.toUiPathProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import java.nio.file.Paths
import kotlin.io.path.Path


class GhidraStep(parent: NewProjectWizardStep) :
    AbstractNewProjectWizardStep(parent), GhidraData {
    // Whether project is a Ghidra script or module
    override val typeProperty = propertyGraph.property(GhidraProjectType.Module)
    override var type: GhidraProjectType by typeProperty
    // Path to Ghidra installation
    override val pathProperty = propertyGraph.property("")
    override var path: String by pathProperty
    // JDK to use
    override val jdkIntentProperty = propertyGraph.property<ProjectWizardJdkIntent>(ProjectWizardJdkIntent.NoJdk)
    override var jdkIntent by jdkIntentProperty
    val sdkDownloadTaskProperty = jdkIntentProperty.transform { intent -> intent.downloadTask }
    val sdkDownloadTask by sdkDownloadTaskProperty
    // Ghidra modules
    override val ghidraModulesProperty = propertyGraph.property<Map<String, String>>(emptyMap())
    override var ghidraModules: Map<String, String> by ghidraModulesProperty
    // Add sample code or not
    override val addSampleCodeProperty = propertyGraph.property(true)
    override var addSampleCode: Boolean by addSampleCodeProperty

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
        builder.row {
            checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                .bindSelected(addSampleCodeProperty)
                .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
                .onApply { logAddSampleCodeFinished(addSampleCode) }
        }
    }

    fun setupJavaSdkUI(builder: Panel) {
        builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
            projectWizardJdkComboBox(this, jdkIntentProperty)
                .whenItemSelectedFromUi { jdkIntent.javaVersion?.let { logSdkChanged(it.feature) } }
                .onApply { jdkIntent.javaVersion?.let { logSdkFinished(it.feature) } }
        }.bottomGap(BottomGap.SMALL)
    }

    override fun setupProject(project: Project) {
        super.setupProject(project)
        val ghidraSettings = requireNotNull(GhidraSettings.getInstance(project))
        ghidraSettings.path = path
        GhidraNewProjectWizardState.lastPath = path

        // Set up Intellij Module. This will be overwritten by Gradle if a Ghidra Module project is being created.
        // Still left it here since it also handles the downloading of JDK.
        val builder = JavaModuleBuilder()
        configureModuleBuilder(project, builder)
        val module = setupProjectFromBuilder(project, builder)
        module?.let(::startJdkDownloadIfNeeded)
        ApplicationManager.getApplication().runWriteAction {
            val ghidraLib = ghidraSettings.syncGhidraLibrary(project)
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

        if (!context.isCreatingNewProject) {
            // New module in an existing project: set module JDK
            val isSameSdk = ProjectRootManager.getInstance(project).projectSdk?.name == jdkIntent.name
            builder.moduleJdk = if (isSameSdk) null else context.projectJdk
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
            alternativeJrePath = ProjectRootManager.getInstance(project).projectSdk?.homePath
        }
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
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