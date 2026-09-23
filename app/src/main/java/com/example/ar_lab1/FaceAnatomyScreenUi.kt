package com.example.ar_lab1

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.frontCameraConfig
import io.github.sceneview.math.colorOf
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.withSign

@Composable
fun FaceAnatomyScreen() {
    var face by remember { mutableStateOf<AugmentedFace?>(null) }
    var faceInfo by remember { mutableStateOf(FaceInfo()) }
    var mode by remember { mutableStateOf(FaceMode.MESH) }
    var facePoints by remember { mutableStateOf<List<FacePoint>>(emptyList()) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)

    val faceMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            colorOf(
                r = 0.2f,
                g = 0.8f,
                b = 1.0f,
                a = 0.15f
            )
        )
    }

    val pointMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            colorOf(
                r = 1.0f,
                g = 0.1f,
                b = 0.1f,
                a = 1.0f
            )
        )
    }

    val regionMaterials = remember(materialLoader) {
        mapOf(
            FaceRegion.FOREHEAD to materialLoader.createColorInstance(
                colorOf(
                    r = 0.8f,
                    g = 0.2f,
                    b = 1.0f,
                    a = 0.5f
                )
            ),

            FaceRegion.LEFT_EYE to materialLoader.createColorInstance(
                colorOf(
                    r = 0.1f,
                    g = 0.5f,
                    b = 1.0f,
                    a = 0.5f
                )
            ),

            FaceRegion.RIGHT_EYE to materialLoader.createColorInstance(
                colorOf(
                    r = 0.1f,
                    g = 0.8f,
                    b = 1.0f,
                    a = 0.5f
                )
            ),

            FaceRegion.NOSE to materialLoader.createColorInstance(
                colorOf(
                    r = 1.0f,
                    g = 0.2f,
                    b = 0.1f,
                    a = 0.5f
                )
            ),

            FaceRegion.MOUTH to materialLoader.createColorInstance(
                colorOf(
                    r = 1.0f,
                    g = 0.1f,
                    b = 0.5f,
                    a = 0.5f
                )
            ),

            FaceRegion.LEFT_CHEEK to materialLoader.createColorInstance(
                colorOf(
                    r = 1.0f,
                    g = 0.6f,
                    b = 0.1f,
                    a = 0.5f
                )
            ),

            FaceRegion.RIGHT_CHEEK to materialLoader.createColorInstance(
                colorOf(
                    r = 1.0f,
                    g = 0.8f,
                    b = 0.1f,
                    a = 0.5f
                )
            )
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = false,
            engine = engine,
            materialLoader = materialLoader,
            sessionFeatures = setOf(Session.Feature.FRONT_CAMERA),
            sessionCameraConfig = ::frontCameraConfig,
            augmentedFaceMode = Config.AugmentedFaceMode.MESH3D,
            sessionConfiguration = { _, config ->
                config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            },
            onSessionUpdated = { session, _ ->
                val trackedFace = session
                    .getAllTrackables(AugmentedFace::class.java)
                    .firstOrNull { it.trackingState == TrackingState.TRACKING }

                face = trackedFace
                if (trackedFace != null) {
                    facePoints = getFacePoints(trackedFace)

                    val pose = trackedFace.centerPose

                    val quaternion = pose.rotationQuaternion

                    val (pitch, yaw, roll) = quaternionToEuler(
                        x = quaternion[0],
                        y = quaternion[1],
                        z = quaternion[2],
                        w = quaternion[3]
                    )

                    faceInfo = FaceInfo(
                        tracking = true,
                        vertexCount = facePoints.size,
                        centerX = pose.tx(),
                        centerY = pose.ty(),
                        centerZ = pose.tz(),
                        pitch = pitch,
                        yaw = yaw,
                        roll = roll
                    )
                } else {
                    facePoints = emptyList()
                    faceInfo = FaceInfo()
                }
            }
        ) {
            face?.let { trackedFace ->

                when (mode) {

                    FaceMode.MESH -> {
                        AugmentedFaceNode(
                            augmentedFace = trackedFace,
                            meshMaterialInstance = faceMaterial
                        )
                    }

                    FaceMode.POINTS -> {
                        face?.let { trackedFace ->
                            val pose = trackedFace.centerPose
                            facePoints.forEach { point ->
                                val worldPoint = pose.transformPoint(
                                    floatArrayOf(
                                        point.x,
                                        point.y,
                                        point.z
                                    )
                                )

                                SphereNode(
                                    radius = 0.0015f,
                                    materialInstance = pointMaterial,
                                    position = io.github.sceneview.math.Position(
                                        x = worldPoint[0],
                                        y = worldPoint[1],
                                        z = worldPoint[2]
                                    )
                                )
                            }
                        }
                    }

                    FaceMode.INFO -> {
                        FaceInfoPanel(faceInfo = faceInfo)
                    }

                    FaceMode.REGIONS -> {
                        ColoredAugmentedFaceNode(
                            augmentedFace = trackedFace,
                            regionMaterials = regionMaterials
                        )
                    }
                }
            }
        }

        Overlay(
            face = face,
            mode = mode,
            onModeChanged = { mode = it }
        )
    }
}

private fun getFacePoints(face: AugmentedFace): List<FacePoint> {
    val buffer = face.meshVertices.duplicate()
    buffer.rewind()

    val points = ArrayList<FacePoint>(buffer.remaining() / 3)
    var index = 0
    while (buffer.remaining() >= 3) {
        points += FacePoint(
            index = index,
            x = buffer.get(),
            y = buffer.get(),
            z = buffer.get()
        )
        index++
    }
    return points
}

private fun quaternionToEuler(
    x: Float,
    y: Float,
    z: Float,
    w: Float
): Triple<Float, Float, Float> {

    // Pitch — вращение вокруг X
    val sinPitch = 2f * (w * x + y * z)
    val cosPitch = 1f - 2f * (x * x + y * y)
    val pitch = atan2(sinPitch, cosPitch)

    // Yaw — вращение вокруг Y
    val sinYaw = 2f * (w * y - z * x)
    val yaw = if (abs(sinYaw) >= 1f) {
        (PI.toFloat() / 2f).withSign(sinYaw)
    } else {
        asin(sinYaw)
    }

    // Roll — вращение вокруг Z
    val sinRoll = 2f * (w * z + x * y)
    val cosRoll = 1f - 2f * (y * y + z * z)
    val roll = atan2(sinRoll, cosRoll)

    val radiansToDegrees = 180f / PI.toFloat()

    return Triple(
        pitch * radiansToDegrees,
        yaw * radiansToDegrees,
        roll * radiansToDegrees
    )
}
@Composable
private fun Overlay(
    face: AugmentedFace?,
    mode: FaceMode,
    onModeChanged: (FaceMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        FaceStatus(face = face)
        BottomPanel(
            face = face,
            mode = mode,
            onModeChanged = onModeChanged
        )
    }
}

@Composable
private fun FaceStatus(
    face: AugmentedFace?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.70f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "AR FACE ANATOMY",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (face != null) "● TRACKING" else "● SEARCHING...",
                    color = if (face != null) Color.Green else Color.Yellow,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Face mesh",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun BottomPanel(
    face: AugmentedFace?,
    mode: FaceMode,
    onModeChanged: (FaceMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.75f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = when (mode) {
                    FaceMode.MESH ->
                        if (face == null) {
                            "Point the front camera at your face"
                        } else {
                            "3D face mesh detected"
                        }

                    FaceMode.POINTS -> "Face landmarks"
                    FaceMode.INFO -> "Face tracking data"
                    FaceMode.REGIONS -> "Face regions"
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ModeChip(
                    text = "MESH",
                    selected = mode == FaceMode.MESH,
                    onClick = { onModeChanged(FaceMode.MESH) }
                )

                ModeChip(
                    text = "POINTS",
                    selected = mode == FaceMode.POINTS,
                    onClick = { onModeChanged(FaceMode.POINTS) }
                )

                ModeChip(
                    text = "INFO",
                    selected = mode == FaceMode.INFO,
                    onClick = { onModeChanged(FaceMode.INFO) }
                )

                ModeChip(
                    text = "REGIONS",
                    selected = mode == FaceMode.REGIONS,
                    onClick = { onModeChanged(FaceMode.REGIONS) }
                )
            }
        }
    }
}

@Composable
private fun FaceInfoPanel(faceInfo: FaceInfo) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        InfoRow(
            name = "Tracking",
            value = if (faceInfo.tracking) "TRACKING" else "NO FACE"
        )

        InfoRow(
            name = "Vertices",
            value = faceInfo.vertexCount.toString()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "FACE CENTER",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        InfoRow(
            name = "X",
            value = "%.3f m".format(faceInfo.centerX)
        )

        InfoRow(
            name = "Y",
            value = "%.3f m".format(faceInfo.centerY)
        )

        InfoRow(
            name = "Z",
            value = "%.3f m".format(faceInfo.centerZ)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "HEAD ROTATION",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        InfoRow(
            name = "Pitch",
            value = "%.1f°".format(faceInfo.pitch)
        )

        InfoRow(
            name = "Yaw",
            value = "%.1f°".format(faceInfo.yaw)
        )

        InfoRow(
            name = "Roll",
            value = "%.1f°".format(faceInfo.roll)
        )
    }
}

@Composable
private fun InfoRow(
    name: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = Color.LightGray
        )

        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) }
    )
}