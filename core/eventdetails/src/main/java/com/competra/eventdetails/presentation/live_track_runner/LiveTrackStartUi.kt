package com.competra.eventdetails.presentation.live_track_runner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow

/**
 * Согласие на публикацию трека — один раз, перед первым включением трекинга (детали соревнования,
 * карточка «Сегодня старт»).
 */
@Composable
fun LiveTrackConsentDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Онлайн-трек") },
        text = {
            Text(
                "Пока вы на дистанции, телефон будет передавать ваше местоположение. Трек увидят " +
                    "все зрители соревнования в приложении и на сайте Competra, после финиша он " +
                    "останется в архиве соревнования. Если на старте нет интернета, трек запишется " +
                    "в телефоне и загрузится, когда появится связь.\n\nКарту и своё положение во время " +
                    "забега вы не увидите — это требование правил. Остановить запись можно в любой момент."
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Согласен, включить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Проверяет и при необходимости запрашивает разрешения по каждому событию [requests] (перед стартом
 * трека): точная геолокация обязательна, уведомления (Android 13+) — чтобы было видно, что запись
 * идёт. Итог — в [onResult]: `true`, если точная геолокация разрешена.
 */
@Composable
fun LiveTrackPermissionsEffect(requests: Flow<Unit>, onResult: (locationGranted: Boolean) -> Unit) {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentOnResult.value(context.hasFineLocation())
    }
    LaunchedEffect(requests) {
        requests.collect {
            val missing = liveTrackPermissions().filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                currentOnResult.value(true)
            } else {
                launcher.launch(missing.toTypedArray())
            }
        }
    }
}

private fun liveTrackPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun Context.hasFineLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
