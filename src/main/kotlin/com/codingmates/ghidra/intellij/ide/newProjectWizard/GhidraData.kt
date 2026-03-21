package com.codingmates.ghidra.intellij.ide.newProjectWizard

import com.codingmates.ghidra.intellij.ide.model.createApplicationLayoutProxy
import com.codingmates.ghidra.intellij.ide.model.resolveGhidraModuleJar
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.Key
import com.intellij.util.lang.UrlClassLoader
import java.io.File
import kotlin.io.path.Path


interface GhidraData {
    val type: GhidraProjectType
    val typeProperty: ObservableMutableProperty<GhidraProjectType>
    val path: String
    val pathProperty: ObservableMutableProperty<String>
    val jdkIntent: ProjectWizardJdkIntent
    val jdkIntentProperty: ObservableMutableProperty<ProjectWizardJdkIntent>
    var ghidraModules: Map<String, String>
    val ghidraModulesProperty: ObservableMutableProperty<Map<String, String>>
    var addSampleCode: Boolean
    val addSampleCodeProperty: ObservableMutableProperty<Boolean>

    companion object {
        val KEY: Key<GhidraData> = Key.create(GhidraData::class.java.name)
    }

    fun resolve() {
        val utilsJar = Path(path).resolveGhidraModuleJar("Framework", "Utility")
        val utilsClassLoader = UrlClassLoader.build().files(listOf(utilsJar)).get()
        val layout = createApplicationLayoutProxy(utilsClassLoader, File(path))
        ghidraModules = layout.modules.mapValues { it.value.moduleRoot.canonicalPath }
    }
}