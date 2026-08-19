package com.veyro.p2p.features.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.veyro.p2p.protocol.CustomCommandEvent
import com.veyro.p2p.protocol.ExecutionTypeCategory

internal data class CommandExecutionResult(
    val succeeded: Boolean,
    val message: String
)

internal class SafeCustomCommandExecutor(context: Context) {
    private val appContext = context.applicationContext

    fun execute(event: CustomCommandEvent): CommandExecutionResult {
        if (event.executionTypeCategory == ExecutionTypeCategory.RAW_SHELL_COMMAND) {
            return CommandExecutionResult(false, "Shell arbitrário bloqueado por segurança.")
        }
        if (event.executionTypeCategory != ExecutionTypeCategory.NATIVE_BROADCAST_INTENT) {
            return CommandExecutionResult(false, "Categoria de comando não permitida.")
        }

        return runCatching {
            when (event.encodedCommandString) {
                ACTION_VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
                ACTION_VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
                ACTION_TORCH_ON -> setTorch(enabled = true)
                ACTION_TORCH_OFF -> setTorch(enabled = false)
                else -> CommandExecutionResult(false, "Ação fora da lista permitida.")
            }
        }.getOrElse { error ->
            CommandExecutionResult(false, error.localizedMessage ?: "Falha ao executar ação nativa.")
        }
    }

    private fun adjustVolume(direction: Int): CommandExecutionResult {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        return CommandExecutionResult(true, "Volume de mídia ajustado.")
    }

    private fun setTorch(enabled: Boolean): CommandExecutionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return CommandExecutionResult(false, "Lanterna indisponível nesta versão do Android.")
        }
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return CommandExecutionResult(false, "Permissão da câmera necessária para a lanterna.")
        }

        val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return CommandExecutionResult(false, "Este aparelho não possui lanterna disponível.")
        manager.setTorchMode(cameraId, enabled)
        return CommandExecutionResult(
            true,
            if (enabled) "Lanterna ligada." else "Lanterna desligada."
        )
    }

    companion object {
        const val ACTION_VOLUME_UP = "VEYRO_VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "VEYRO_VOLUME_DOWN"
        const val ACTION_TORCH_ON = "VEYRO_TORCH_ON"
        const val ACTION_TORCH_OFF = "VEYRO_TORCH_OFF"
    }
}
