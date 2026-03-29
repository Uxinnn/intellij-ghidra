package com.codingmates.ghidra.intellij.ide.newProjectWizard

import com.intellij.ide.projectWizard.ProjectWizardJdkIntent
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.Key


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
}