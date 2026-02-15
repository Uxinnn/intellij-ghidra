package com.codingmates.ghidra.intellij.ide.newProjectWizard

import com.codingmates.ghidra.intellij.ide.icons.GhidraIcons
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import javax.swing.Icon
import com.intellij.ide.wizard.GitNewProjectWizardStep


class GhidraNewProjectWizard : GeneratorNewProjectWizard {
    override val id: String = "Ghidra"

    override val name: String = "New Ghidra Module"

    override val icon: Icon = GhidraIcons.Ghidra

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::GitNewProjectWizardStep)
            .nextStep(::GhidraStep)
            .nextStep(::AssetsStep)

    class Builder : GeneratorNewProjectWizardBuilderAdapter(GhidraNewProjectWizard()) {
        override fun getWeight(): Int = JVM_WEIGHT + 100
    }
}