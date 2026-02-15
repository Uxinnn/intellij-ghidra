package com.codingmates.ghidra.intellij.ide.newProjectWizard

import com.codingmates.ghidra.intellij.ide.GhidraBundle
import com.codingmates.ghidra.intellij.ide.newProjectWizard.GhidraData.Companion.ghidraData
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraLauncherConfiguration
import com.codingmates.ghidra.intellij.ide.runConfiguration.GhidraLauncherConfigurationType
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.ui.setEmptyState
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


class GhidraStep(parent: NewProjectWizardStep) :
    AbstractNewProjectWizardStep(parent), GhidraData {
    // Whether project is a Ghidra script or module
    private val typeProperty = propertyGraph.property(GhidraProjectType.Module)
    override var type: GhidraProjectType by typeProperty
    // Path to Ghidra installation
    private val pathProperty = propertyGraph.property("")
    override var path: String by pathProperty
    private val ghidraPathField = TextFieldWithBrowseButton()
    // JDK to use
    private val sdkProperty = propertyGraph.property<Sdk?>(null)
    override var sdk: Sdk? by sdkProperty
    // Ghidra modules
    private val ghidraModulesProperty = propertyGraph.property<Map<String, String>>(emptyMap())
    override var ghidraModules: Map<String, String> by ghidraModulesProperty


    init {
        data.putUserData(GhidraData.KEY, this)
    }

    override fun setupUI(builder: Panel) {
        with(builder) {
            row(GhidraBundle.message("ghidra.facet.editor.installation")) {
                val title = GhidraBundle.message("ghidra.facet.editor.installation.dialog.title")
                val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withPathToTextConvertor(::getPresentablePath)
                    .withTextToPathConvertor(::getCanonicalPath)
                    .withTitle(title)
                ghidraPathField.addBrowseFolderListener(
                    title,
                    GhidraBundle.message("ghidra.facet.editor.installation.dialog.desc"),
                    context.project,
                    fileChooserDescriptor
                )
                cell(ghidraPathField)
                    .applyToComponent { setEmptyState(GhidraBundle.message("ghidra.facet.editor.installation.empty")) }
                    .align(AlignX.FILL)
            }
            row("Project Type:") {
                comboBox(GhidraProjectType.entries).bindItem(typeProperty)
            }
            row("JDK: ") {  // Handles the setting of project JDK too
                sdkComboBox(context, sdkProperty, "Ghidra JDK")
            }
        }
    }

    override fun setupProject(project: Project) {
        super.setupProject(project)
        pathProperty.set(ghidraPathField.text)
        ghidraData?.resolve()

        createRunConfigInstance(project)
        if (type == GhidraProjectType.Script) {
            val module = createRootModule(project)

            ApplicationManager.getApplication().runWriteAction {
                val ghidraLib = createGhidraLibrary(project)
                ModuleRootModificationUtil.addDependency(module, ghidraLib)
            }
        }
    }

    private fun createRunConfigInstance(project: Project) {
        // Create run configuration entry for the project
        val runManager = RunManager.getInstance(project)

        val factory = ConfigurationTypeUtil
            .findConfigurationType(GhidraLauncherConfigurationType::class.java)
            .configurationFactories
            .firstOrNull()
            ?: error("Failed to create GhidraLauncherConfigurationType")
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

    private fun createRootModule(project: Project): Module {
        // Create new module
        val moduleManager = ModuleManager.getInstance(project)
        val basePath = Path(project.basePath ?: error("Cannot find project base path."))
        val modulePath = basePath.resolve("${project.name}.iml")
        val module = WriteAction.compute<Module, Throwable> {
            val modifiableModel = moduleManager.getModifiableModel()
            val newModule = modifiableModel.newModule(modulePath, StdModuleTypes.JAVA.id)
            modifiableModel.commit()
            newModule
        }

        // Configure new module
        WriteAction.run<RuntimeException> {
            ModuleRootModificationUtil.updateModel(module) { rootModel ->
                val baseDir = project.baseDir!!
                val contentEntry = rootModel.addContentEntry(baseDir)
                contentEntry.addSourceFolder(baseDir, false)
                sdkProperty.get()?.let { sdk ->
                    rootModel.sdk = sdk
                }
            }
        }

        return module
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
}