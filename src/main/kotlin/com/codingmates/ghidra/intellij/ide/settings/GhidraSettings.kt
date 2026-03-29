package com.codingmates.ghidra.intellij.ide.settings

import com.codingmates.ghidra.intellij.ide.model.createApplicationLayoutProxy
import com.codingmates.ghidra.intellij.ide.model.resolveGhidraModuleJar
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xmlb.XmlSerializerUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path


@Service(Service.Level.PROJECT)
@State(
    name = "com.codingmates.ghidra.intellij.ide.settings.GhidraSettings",
    storages = [Storage("GhidraSettings.xml")]
)
class GhidraSettings: PersistentStateComponent<GhidraSettings> {
    var path: String = ""
        set(value) { field = FileUtil.toSystemIndependentName(value) }

    companion object {
        fun getInstance(project: Project): GhidraSettings =
            project.getService(GhidraSettings::class.java)
    }

    override fun getState(): GhidraSettings = this

    override fun loadState(newState: GhidraSettings) {
        XmlSerializerUtil.copyBean(newState, this)
    }

    fun syncGhidraLibrary(project: Project): Library {
        val library = getGhidraLibrary(project)
        updateGhidraLibrary(library)
        return library
    }

    /**
     * Gets the Ghidra library. Creates it if it does not exist.
     */
    fun getGhidraLibrary(
        project: Project
    ): Library {
        val registrar = LibraryTablesRegistrar.getInstance()
        val projectLibTable = registrar.getLibraryTable(project)
        val libModel = projectLibTable.modifiableModel
        val library = projectLibTable.getLibraryByName("Ghidra")
            ?: libModel.createLibrary("Ghidra")
        libModel.commit()
        return library
    }

    fun updateGhidraLibrary(library: Library) {
        library.modifiableModel.apply {
            val ghidraModules = getGhidraModules()
            val classRoots: List<VirtualFile> = getClassRoots(ghidraModules)
            val sourceRoots: List<VirtualFile> = getSourceRoots(ghidraModules)

            listOf(OrderRootType.CLASSES, OrderRootType.SOURCES).forEach { rootType ->
                getUrls(rootType).forEach { removeRoot(it, rootType) }
            }

            classRoots.forEach { addRoot(it, OrderRootType.CLASSES) }
            sourceRoots.forEach { addRoot(it, OrderRootType.SOURCES) }
        }.commit()
    }

    private fun getSourceRoots(ghidraModules: Map<String, String>): List<VirtualFile> {
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

    private fun getClassRoots(ghidraModules: Map<String, String>): List<VirtualFile> {
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

    fun getGhidraModules(): Map<String, String> {
        val utilsJar = Path(path).resolveGhidraModuleJar("Framework", "Utility")
        val utilsClassLoader = UrlClassLoader.build().files(listOf(utilsJar)).get()
        val layout = createApplicationLayoutProxy(utilsClassLoader, File(path))
        val ghidraModules = layout.modules.mapValues { it.value.moduleRoot.canonicalPath }
        return ghidraModules
    }
}