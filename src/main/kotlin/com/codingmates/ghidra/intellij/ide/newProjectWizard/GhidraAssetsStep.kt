package com.codingmates.ghidra.intellij.ide.newProjectWizard

import com.codingmates.ghidra.intellij.ide.newProjectWizard.GhidraData.Companion.ghidraData
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project


class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {

    override fun setupAssets(project: Project) {
        if (ghidraData?.type == GhidraProjectType.Module) setupModuleAssets(project) else setupScriptAssets()
    }

    fun setupModuleAssets(project: Project) {
        val name = baseData?.name
        // Have to use this to inject the default props to the templates since apparently `addTemplateAsset`
        // doesn't do it.
        val props = FileTemplateManager.getInstance(project).defaultProperties.entries.associate {
            it.key.toString() to it.value
        }

        addTemplateAsset("build.gradle", "build.gradle", emptyMap())
        addTemplateAsset("extension.properties", "extension.properties", emptyMap())
        addTemplateAsset("Module.manifest", "Module.manifest", emptyMap())
        addTemplateAsset("README.md", "README.md", emptyMap())
        // data dir
        addTemplateAsset("data/buildLanguage.xml", "buildLanguage.xml", emptyMap())
        addTemplateAsset("data/README.txt", "data_README.txt", emptyMap())
        addTemplateAsset("data/sleighArgs.txt", "data__sleighArgs.txt", emptyMap())
        addTemplateAsset("data/languages/skel.cspec", "skel.cspec", emptyMap())
        addTemplateAsset("data/languages/skel.ldefs", "skel.ldefs", emptyMap())
        addTemplateAsset("data/languages/skel.opinion", "skel.opinion", emptyMap())
        addTemplateAsset("data/languages/skel.pspec", "skel.pspec", emptyMap())
        addTemplateAsset("data/languages/skel.sinc", "skel.sinc", emptyMap())
        addTemplateAsset("data/languages/skel.slaspec", "skel.slaspec", emptyMap())
        // ghidra_scripts dir
        addTemplateAsset("ghidra_scripts/README.txt", "ghidra_scripts_README.txt", emptyMap())
        addTemplateAsset("ghidra_scripts/sample_script.py", "sample_script.py", emptyMap())
        addTemplateAsset("ghidra_scripts/SampleScript.java", "SampleScript.java", emptyMap())
        // lib dir
        addTemplateAsset("lib/README.txt", "lib_README.txt", emptyMap())
        // os dir
        addTemplateAsset("os/linux_x86_64/README.txt", "linux_x86_64_README.txt", emptyMap())
        addTemplateAsset("os/mac_x86_64/README.txt", "mac_x86_64_README.txt", emptyMap())
        addTemplateAsset("os/win_x86_64/README.txt", "win_x86_64_README.txt", emptyMap())
        // src dir
        addTemplateAsset("src/main/help/help/topics/skeleton/help.html", "help.html", emptyMap())
        addTemplateAsset("src/main/help/help/TOC_Source.xml", "TOC_Source.xml", emptyMap())
        addTemplateAsset("src/main/resources/images/README.txt", "images_README.txt", emptyMap())
        addTemplateAsset("src/main/java/$name/${name}Analyzer.java", "nameAnalyzer.java", props)
        addTemplateAsset("src/main/java/$name/${name}Exporter.java", "nameExporter.java", props)
        addTemplateAsset("src/main/java/$name/${name}FileSystem.java", "nameFileSystem.java", props)
        addTemplateAsset("src/main/java/$name/${name}Loader.java", "nameLoader.java", props)
        addTemplateAsset("src/main/java/$name/${name}Plugin.java", "namePlugin.java", props)
    }

    fun setupScriptAssets() {
        addTemplateAsset("README.txt", "ghidra_scripts_README.txt", emptyMap())
        addTemplateAsset("sample_script.py", "sample_script.py", emptyMap())
        addTemplateAsset("SampleScript.java", "SampleScript.java", emptyMap())
    }
}