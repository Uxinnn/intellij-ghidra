package com.codingmates.ghidra.intellij.ide.newProjectWizard

import com.codingmates.ghidra.intellij.ide.model.createApplicationLayoutProxy
import com.codingmates.ghidra.intellij.ide.model.resolveGhidraModuleJar
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.util.Key
import com.intellij.util.lang.UrlClassLoader
import java.io.File
import kotlin.io.path.Path


interface GhidraData {
    val type: GhidraProjectType
    val typeProperty: GraphProperty<GhidraProjectType>
    val path: String
    val pathProperty: GraphProperty<String>
    val sdk: Sdk?
    val sdkProperty: GraphProperty<Sdk?>
    var ghidraModules: Map<String, String>
    val ghidraModulesProperty: GraphProperty<Map<String, String>>
    val sdkDownloadTaskProperty: ObservableMutableProperty<SdkDownloadTask?>
    var sdkDownloadTask: SdkDownloadTask?

    companion object {
        val KEY: Key<GhidraData> = Key.create(GhidraData::class.java.name)

        @JvmStatic
        val NewProjectWizardStep.ghidraData: GhidraData?
            get() = data.getUserData(KEY)
    }

    fun resolve() {
        val utilsJar = Path(path).resolveGhidraModuleJar("Framework", "Utility")
        val utilsClassLoader = UrlClassLoader.build().files(listOf(utilsJar)).get()
        val layout = createApplicationLayoutProxy(utilsClassLoader, File(path))
        ghidraModules = layout.modules.mapValues { it.value.moduleRoot.canonicalPath }
    }
}