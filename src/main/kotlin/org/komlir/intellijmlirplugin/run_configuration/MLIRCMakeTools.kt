package org.komlir.intellijmlirplugin.run_configuration

import com.jetbrains.cidr.cpp.cmake.model.CMakeConfiguration
import com.jetbrains.cidr.cpp.cmake.model.CMakeTarget

/** A run uses one profile for every tool, including downstream CMake executables. */
internal class MLIRCMakeTools(private val targets: List<CMakeTarget>, selectedProfile: String?) {
    data class Tool(val target: CMakeTarget, val configuration: CMakeConfiguration)
    private var profile = selectedProfile
    private val resolved = linkedMapOf<String, Tool>()

    fun resolve(name: String, required: Boolean): Tool? {
        resolved[name]?.let { return it }
        val target = targets.firstOrNull { it.name == name }
            ?: if (required) error("No CMake target named '$name'.") else return null
        val configuration = if (profile != null) {
            target.buildConfigurations.firstOrNull { it.name == profile }
                ?: error("Target '$name' is not available in profile '$profile'.")
        } else target.buildConfigurations.firstOrNull() ?: error("No build configuration for '$name'.")
        if (configuration.productFile == null || configuration.targetType != CMakeConfiguration.TargetType.EXECUTABLE) {
            error("CMake target '$name' has no executable in profile '${configuration.name}'.")
        }
        profile = configuration.name
        return Tool(target, configuration).also { resolved[name] = it }
    }
}
