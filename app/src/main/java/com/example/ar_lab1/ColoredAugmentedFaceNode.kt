package com.example.ar_lab1

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneScope
import io.github.sceneview.ar.node.AugmentedFaceNode
import io.github.sceneview.node.MeshNode
import java.nio.ByteBuffer
import java.nio.ByteOrder

private class RegionFaceOverlay(
    private val engine: Engine,
    private val parentNode: io.github.sceneview.node.Node,
    private val materials: Map<FaceRegion, MaterialInstance>
) {
    private var vertexBuffer: VertexBuffer? = null
    private val regionNodes = mutableListOf<MeshNode>()
    private val indexBuffers = mutableListOf<IndexBuffer>()

    fun update(face: AugmentedFace) {
        val vertices = face.meshVertices
            .duplicate()
            .apply { rewind() }

        if (vertices.limit() == 0) return

        if (vertexBuffer == null) {
            val vertexCount = vertices.limit() / 3
            val buffer =
                VertexBuffer.Builder()
                    .vertexCount(vertexCount)
                    .bufferCount(1)
                    .attribute(
                        VertexBuffer.VertexAttribute.POSITION,
                        0,
                        VertexBuffer.AttributeType.FLOAT3
                    )
                    .build(engine)

            buffer.setBufferAt(engine, 0, vertices)
            vertexBuffer = buffer
            createRegionNodes(face)
        } else {
            vertexBuffer?.setBufferAt(engine, 0, vertices)
        }
    }

    private fun createRegionNodes(face: AugmentedFace) {

        val sharedVertexBuffer = vertexBuffer ?: return
        val indicesByRegion = buildRegionIndicesByRegion(face)
        FaceRegions.all.keys.forEach { region ->
            val indices = indicesByRegion[region] ?: return@forEach
            if (indices.isEmpty()) return@forEach

            val material = materials[region]?: return@forEach

            val indexBuffer = IndexBuffer.Builder()
                    .indexCount(indices.size)
                    .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                    .build(engine)

            val indexData = ByteBuffer
                    .allocateDirect(indices.size * Short.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()

            indexData.put(indices)
            indexData.rewind()

            indexBuffer.setBuffer(engine, indexData)
            indexBuffers += indexBuffer

            val meshNode =
                MeshNode(
                    engine = engine,
                    primitiveType = RenderableManager.PrimitiveType.TRIANGLES,
                    vertexBuffer = sharedVertexBuffer,
                    indexBuffer = indexBuffer,
                    boundingBox = null,
                    materialInstance = material,
                    builder = {
                        culling(false)
                        castShadows(false)
                        receiveShadows(false)
                    }
                )

            meshNode.parent = parentNode
            regionNodes += meshNode
        }
    }

    private fun buildRegionIndicesByRegion(face: AugmentedFace): Map<FaceRegion, ShortArray> {
        val verticesBuffer = face.meshVertices.duplicate()
        verticesBuffer.rewind()

        val vertices = FloatArray(verticesBuffer.remaining())
        verticesBuffer.get(vertices)

        val trianglesBuffer = face.meshTriangleIndices.duplicate()
        trianglesBuffer.rewind()

        val result = FaceRegions.all.keys.associateWith {
            ArrayList<Short>()
        }

        //Находим границы всего лица.
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        var i = 0

        while (i < vertices.size) {
            val x = vertices[i]
            val y = vertices[i + 1]

            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)

            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)

            i += 3
        }

        val faceWidth = maxX - minX
        val faceHeight = maxY - minY

        fun normalizeX(x: Float): Float {
            return ((x - minX) / faceWidth)
                .coerceIn(0f, 1f)
        }

        fun normalizeY(y: Float): Float {
            return 1f - (
                    (y - minY) / faceHeight
                    ).coerceIn(0f, 1f)
        }

        fun ellipseDistance(
            x: Float,
            y: Float,
            centerX: Float,
            centerY: Float,
            radiusX: Float,
            radiusY: Float
        ): Float {
            val dx = (x - centerX) / radiusX
            val dy = (y - centerY) / radiusY
            return dx * dx + dy * dy
        }

        while (trianglesBuffer.remaining() >= 3) {
            val a = trianglesBuffer.get().toInt()
            val b = trianglesBuffer.get().toInt()
            val c = trianglesBuffer.get().toInt()

            val ax = normalizeX(vertices[a * 3])
            val ay = normalizeY(vertices[a * 3 + 1])

            val bx = normalizeX(vertices[b * 3])
            val by = normalizeY(vertices[b * 3 + 1])

            val cx = normalizeX(vertices[c * 3])
            val cy = normalizeY(vertices[c * 3 + 1])

            val centerX =
                (ax + bx + cx) / 3f

            val centerY =
                (ay + by + cy) / 3f

            val scores = mapOf(
                FaceRegion.FOREHEAD to
                        ellipseDistance(
                            centerX,
                            centerY,
                            centerX = 0.50f,
                            centerY = 0.17f,
                            radiusX = 0.43f,
                            radiusY = 0.23f
                        ),

                FaceRegion.LEFT_EYE to
                        ellipseDistance(
                            centerX,
                            centerY,
                            centerX = 0.32f,
                            centerY = 0.38f,
                            radiusX = 0.16f,
                            radiusY = 0.09f
                        ),

                FaceRegion.RIGHT_EYE to
                        ellipseDistance(
                            centerX,
                            centerY,
                            centerX = 0.68f,
                            centerY = 0.38f,
                            radiusX = 0.16f,
                            radiusY = 0.09f
                        ),

                FaceRegion.NOSE to
                        ellipseDistance(
                            centerX,
                            centerY,
                            centerX = 0.50f,
                            centerY = 0.51f,
                            radiusX = 0.13f,
                            radiusY = 0.20f
                        ),

                FaceRegion.MOUTH to
                        ellipseDistance(
                            centerX,
                            centerY,
                            centerX = 0.50f,
                            centerY = 0.69f,
                            radiusX = 0.21f,
                            radiusY = 0.12f
                        ),

                FaceRegion.LEFT_CHEEK to
                        ellipseDistance(
                            centerX,
                            centerY,
                            centerX = 0.25f,
                            centerY = 0.59f,
                            radiusX = 0.27f,
                            radiusY = 0.27f
                        ),

                FaceRegion.RIGHT_CHEEK to
                        ellipseDistance(
                            centerX,
                            centerY,
                            centerX = 0.75f,
                            centerY = 0.59f,
                            radiusX = 0.27f,
                            radiusY = 0.27f
                        )
            )

            val region = scores.minBy { it.value }.key

            result[region]?.apply {
                add(a.toShort())
                add(b.toShort())
                add(c.toShort())
            }
        }

        return result.mapValues { (_, indices) ->
            indices.toShortArray()
        }
    }

    fun destroy() {
        regionNodes.forEach { node ->
            node.parent = null
            node.destroy()
        }

        regionNodes.clear()
        indexBuffers.forEach { buffer ->
            runCatching {
                engine.destroyIndexBuffer(buffer)
            }
        }

        indexBuffers.clear()

        vertexBuffer?.let { buffer ->
            runCatching {
                engine.destroyVertexBuffer(buffer)
            }
        }

        vertexBuffer = null
    }
}

private class ColoredAugmentedFaceNodeImpl(
    engine: Engine,
    augmentedFace: AugmentedFace,
    materials: Map<FaceRegion, MaterialInstance>
) : AugmentedFaceNode(
    engine = engine,
    augmentedFace = augmentedFace,
    meshMaterialInstance = null,
    computeTangents = false
) {
    private lateinit var regionOverlay: RegionFaceOverlay

    init {
        meshNode?.isVisible = false
        regionOverlay =
            RegionFaceOverlay(
                engine = engine,
                parentNode = centerNode,
                materials = materials
            )

        if (augmentedFace.trackingState == TrackingState.TRACKING) {
            regionOverlay.update(augmentedFace)
        }
    }

    override fun update(
        trackable: AugmentedFace?
    ) {
        super.update(trackable)
        if (!::regionOverlay.isInitialized) return

        meshNode?.isVisible = false
        if (augmentedFace.trackingState == TrackingState.TRACKING) {
            regionOverlay.update(augmentedFace)
        }
    }

    override fun destroy() {
        regionOverlay.destroy()
        super.destroy()
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun ARSceneScope.ColoredAugmentedFaceNode(
    augmentedFace: AugmentedFace,
    regionMaterials: Map<FaceRegion, MaterialInstance>
) {
    val node =
        remember(engine, augmentedFace) {
            ColoredAugmentedFaceNodeImpl(
                engine = engine,
                augmentedFace = augmentedFace,
                materials = regionMaterials
            )
        }

    NodeLifecycle(
        node = node,
        content = null
    )
}