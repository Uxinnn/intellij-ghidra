package com.codingmates.ghidra.intellij.ide.settings

import com.codingmates.ghidra.intellij.ide.GhidraBundle
import com.codingmates.ghidra.intellij.ide.model.isGhidraInstallationPath
import com.codingmates.ghidra.intellij.ide.model.isGhidraSourcesPath
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.psi.PsiManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.plugins.gradle.settings.GradleSettings


class GhidraSettingsConfigurable(var project: Project) : BoundConfigurable("GhidraSettingsComponent") {
    private val settings get() = GhidraSettings.getInstance(project)
    private lateinit var pathField: Cell<TextFieldWithBrowseButton>

    override fun apply() {
        validateGhidraPath(pathField.component.text)
        super.apply()
        ApplicationManager.getApplication().runWriteAction {
            val ghidraLib = settings.syncGhidraLibrary(project)
            if (GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty()) {
                writeGhidraPathToGradleProperties(settings.path)
            } else {
                val module = ModuleManager.getInstance(project).findModuleByName(project.name)
                module?.let {
                    val hasGhidraLib = ModuleRootManager.getInstance(module)
                        .orderEntries
                        .filterIsInstance<LibraryOrderEntry>()
                        .any { entry -> entry == ghidraLib }
                    if (!hasGhidraLib) {
                        ModuleRootModificationUtil.addDependency(it, ghidraLib)
                    }
                }
            }
        }
    }

    override fun createPanel(): DialogPanel = panel {
        row(GhidraBundle.message("ghidra.facet.editor.installation")) {
            val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle(GhidraBundle.message("ghidra.facet.editor.installation.dialog.title"))
                .withPathToTextConvertor(::getPresentablePath)
                .withTextToPathConvertor(::getCanonicalPath)
            pathField = textFieldWithBrowseButton(fileChooserDescriptor, project)
                .bindText(settings::path)
                .align(AlignX.FILL)
        }
    }

    private fun writeGhidraPathToGradleProperties(path: String) {
        val gradlePropertiesFile = project.guessProjectDir()
            ?.findOrCreateChildData(this, "gradle.properties") ?: return

        val psiFile = PsiManager.getInstance(project)
            .findFile(gradlePropertiesFile) as? PropertiesFile ?: return

        val existingProperty = psiFile.findPropertyByKey("GHIDRA_INSTALL_DIR")

        WriteCommandAction.runWriteCommandAction(project) {
            if (existingProperty != null) {
                existingProperty.setValue(path)
            } else {
                psiFile.addProperty("GHIDRA_INSTALL_DIR", path)
            }
        }
    }

    private fun validateGhidraPath(path: String) {
        if (!isGhidraInstallationPath(path)) {
            throw ConfigurationException(GhidraBundle.message("ghidra.facet.editor.installation.error.no-properties"))
        }
        if (isGhidraSourcesPath(path)) {
            throw ConfigurationException(GhidraBundle.message("ghidra.facet.editor.installation.error.sources"))
        }
    }
}