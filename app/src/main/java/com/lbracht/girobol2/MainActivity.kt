package com.lbracht.girobol2

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lbracht.girobol2.ui.theme.Girobol2Theme
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

var screenWidth = 0f
var screenHeight = 0f

private var ballPosition by mutableStateOf(Offset(0f, 0f))
private var ballSpeed by mutableStateOf((Offset(0f, 0f)))
private var ballAcceleration by mutableStateOf(Offset(0f, 0f))

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null

    private var lastTimestamp = 0L
    private var currentOrientationX = 0f
    private var currentOrientationY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenHeight = displayMetrics.heightPixels.toFloat()
        screenWidth = displayMetrics.widthPixels.toFloat()
        //ballPosition = Offset(screenWidth/2, screenHeight/2)

        setContent {
            Girobol2Theme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.green_glitter_textured_paper_background__2_),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Log.d("MainActivity",
                        "onCreate: screenWidth, screenHeight: $screenWidth, $screenHeight"
                    )
                    BallGame(screenWidth, screenHeight)
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for gyroscope sensor
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            // Extract the inclination from the rotation matrix
            val inclination = Math.toDegrees(
                -SensorManager.getInclination(rotationMatrix).toDouble()
            ).toFloat()


            // Update ball position based on sensor matrix
            val timestamp = event.timestamp
            val dt = if (lastTimestamp != 0L) (timestamp - lastTimestamp) * 1e-9f else 0f

            val pitch = asin(rotationMatrix[7].toDouble()).toFloat()
            val roll = atan2(rotationMatrix[6].toDouble(), rotationMatrix[8].toDouble()).toFloat()
            val inclinationXY = sqrt(pitch*pitch + roll*roll)

            // TODO: I create difficulty modes (easy, normal hard), by changing these 2 constants and the friction constant below
            var ballAccelerationX = -sin(roll) * Const.GRAVITY_FACTOR
            var ballAccelerationY = sin(pitch) * Const.GRAVITY_FACTOR


            ballAcceleration = Offset(
                ballAccelerationX,
                ballAccelerationY
            )

            // TODO: adjust this constant
            if(inclinationXY < Const.INCLINATION_THRESHOLD && abs(ballSpeed.x) < Const.SPEED_THRESHOLD && abs(ballSpeed.y) < Const.SPEED_THRESHOLD) {
            //if(eventXY < 0.0005) {
                ballAcceleration = Offset(
                    0f, // Adjust based on gyroscope data
                    0f  // Adjust based on gyroscope data
                )
            }

            // TODO: I create difficulty modes (easy, normal hard), by changing these friction constants below
            ballSpeed = Offset(
                ballSpeed.x + ballAcceleration.x * dt - ballSpeed.x.sign * Const.KINETIC_FRICTION * dt, // Adjust based on gyroscope data
                ballSpeed.y + ballAcceleration.y * dt - ballSpeed.y.sign * Const.KINETIC_FRICTION * dt// Adjust based on gyroscope data
            )
            println("ballSpeed -> $ballSpeed")
            ballPosition = Offset(
                ballPosition.x + ballSpeed.x * dt + ballAcceleration.x * dt * dt / 2,
                ballPosition.y + ballSpeed.y * dt + ballAcceleration.y * dt * dt / 2
            )

            lastTimestamp = timestamp

            //Log.d("Sensor Event", "currentOrientation in degrees: x -> $currentOrientationDegreesX")
            //Log.d("Sensor Event", "currentOrientation in degrees: y -> $currentOrientationDegreesY")

            Log.d("Sensor Event", "currentOrientation in degrees: x -> ${-roll}")
            Log.d("Sensor Event", "currentOrientation in degrees: y -> $pitch")
        }
    }
}


@Composable
fun BallGame(screenWidth: Float, screenHeight: Float) {
    //var ballPosition by remember { mutableStateOf(Offset(0f, 0f)) }

    LaunchedEffect(Unit) {
        // Log the screen dimensions for debugging
        println("Screen Width: $screenWidth, Screen Height: $screenHeight")

        // Center the ball initially
        ballPosition = Offset(screenWidth / 2, screenHeight / 2)

        // Log the ball position for debugging
        println("Ball Position: $ballPosition")
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        //contentAlignment = Alignment.Center
    ) {
        Ball(ballPosition)
    }
}

@Composable
fun Ball(position: Offset) {
    Canvas(
        modifier = Modifier
            .absoluteOffset(
                x = with(LocalDensity.current) { position.x.toDp() - Const.BALL_SIZE / 2 },
                y = with(LocalDensity.current) { position.y.toDp() - Const.BALL_SIZE / 2 })
            .size(Const.BALL_SIZE)
            .clip(CircleShape)
    ) {
        drawIntoCanvas {
            drawCircle(color = Color.White)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Girobol2Theme {
        BallGame(screenWidth/2, screenHeight/2)
    }

}














/*

package com.lbracht.girobol2

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lbracht.girobol2.ui.theme.Girobol2Theme
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

var screenWidth = 0f
var screenHeight = 0f

private var ballPosition by mutableStateOf(Offset(0f, 0f))
private var ballSpeed by mutableStateOf((Offset(0f, 0f)))
private var ballAcceleration by mutableStateOf(Offset(0f, 0f))

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroscopeSensor: Sensor? = null

    private var lastTimestamp = 0L
    private var currentOrientationX = 0f
    private var currentOrientationY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenHeight = displayMetrics.heightPixels.toFloat()
        screenWidth = displayMetrics.widthPixels.toFloat()

        setContent {
            Girobol2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BallGame(screenWidth, screenHeight)
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for gyroscope sensor
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // Update ball position based on gyroscope data

            val timestamp = event.timestamp
            val dt = if (lastTimestamp != 0L) (timestamp - lastTimestamp) * 1e-9f else 0f
            lastTimestamp = timestamp

            val angularVelocityX = event.values[1]
            val angularVelocityY = event.values[0]

            val deltaRotationX = angularVelocityX * dt
            val deltaRotationY = angularVelocityY * dt
            val deltaRotationXY = sqrt(deltaRotationX*deltaRotationX + deltaRotationY*deltaRotationY)

            currentOrientationX += deltaRotationX
            currentOrientationY += deltaRotationY


            // TODO: I have to multiply those by a constant
            var ballAccelerationX = sin(currentOrientationX) // TODO: divide the argument by 2? chatgpt said but I dont agree
            var ballAccelerationY = sin(currentOrientationY)


            ballAcceleration = Offset(
                ballAccelerationX,
                ballAccelerationY
            )

            // TODO: adjust this constant
            if(deltaRotationXY < 0.0000 && abs(ballSpeed.x) < 5 && abs(ballSpeed.y) < 5) {
            //if(eventXY < 0.0005) {
                ballAcceleration = Offset(
                    0f, // Adjust based on gyroscope data
                    0f  // Adjust based on gyroscope data
                )
            }

            ballSpeed = Offset(
                ballSpeed.x * 0.985f + ballAcceleration.x, // Adjust based on gyroscope data
                ballSpeed.y * 0.985f + ballAcceleration.y  // Adjust based on gyroscope data
            )
            println("ballSpeed -> " + ballSpeed)
            ballPosition = Offset(ballPosition.x + ballSpeed.x, ballPosition.y + ballSpeed.y)

            val currentOrientationDegreesX = Math.toDegrees(currentOrientationX.toDouble()).toFloat()
            val currentOrientationDegreesY = Math.toDegrees(currentOrientationY.toDouble()).toFloat()

            Log.d("Sensor Event", "currentOrientation in degrees: x -> $currentOrientationDegreesX")
            Log.d("Sensor Event", "currentOrientation in degrees: y -> $currentOrientationDegreesY")
        }
    }
}


@Composable
fun BallGame(screenWidth: Float, screenHeight: Float) {
    //var ballPosition by remember { mutableStateOf(Offset(0f, 0f)) }

    LaunchedEffect(Unit) {
        // Log the screen dimensions for debugging
        println("Screen Width: $screenWidth, Screen Height: $screenHeight")

        // Center the ball initially
        ballPosition = Offset(screenWidth / 2, screenHeight / 2)

        // Log the ball position for debugging
        println("Ball Position: $ballPosition")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(0.dp)
    ) {
        Ball(ballPosition)
    }
}

@Composable
fun Ball(position: Offset) {
    Canvas(
        modifier = Modifier
            .absoluteOffset(
                x = with(LocalDensity.current) { position.x.toDp() - 25.dp },
                y = with(LocalDensity.current) { position.y.toDp() - 25.dp })
            .size(50.dp)
            .clip(CircleShape)
    ) {
        drawIntoCanvas {
            drawCircle(color = Color.Blue)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Girobol2Theme {
        BallGame(screenWidth/2, screenHeight/2)
    }

}


 */