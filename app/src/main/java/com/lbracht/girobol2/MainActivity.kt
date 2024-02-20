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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lbracht.girobol2.ui.theme.Girobol2Theme
import kotlinx.coroutines.delay
import java.util.Random
import java.util.Timer
import kotlin.concurrent.schedule
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
private var currentGoal = Offset(1000000f, 1000000f)
private var nextGoal = true
private var goalRemainingTime by mutableStateOf(3f)
private var randomX = 0f
private var randomY = 0f

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null

    private var lastTimestamp = 0L

    private var remainingTime by mutableStateOf(Const.LEVEL_TIME)

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
                        painter = painterResource(id = R.drawable.felt_green_background),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Log.d("MainActivity", "onCreate: screenWidth, screenHeight: $screenWidth, $screenHeight")
                    BallGame(screenWidth, screenHeight)
                    CircularTimeBar(
                        remainingTime = remember { mutableStateOf(remainingTime) }, // Use remember
                        Color.Blue,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        totalTime = 60
                    )
                    Goal(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                }
            }
        }

        // Start a timer to decrement remaining time every second
        Timer("countdownTimer", false).schedule(1000, 1000) {
            remainingTime--
            if (remainingTime <= 0) {
                cancel() // Stop the timer when time is up
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

            Log.d("MainActivity", "ballPosition.x: ${ballPosition.x}, ballPosition.y: ${ballPosition.y}\n" +
                    "currentGoal.x: ${currentGoal.x}, currentGoal.y: ${currentGoal.y}")
            if(abs(ballPosition.x - currentGoal.x) < Const.GOAL_MARGIN && abs(ballPosition.y - currentGoal.y) < Const.GOAL_MARGIN) {
                //goalRemainingTime -= dt
                Log.d("MainActivity", "onSensorChanged: goalRemainingTime -> $goalRemainingTime")
                goalRemainingTime -= dt

                Log.d("MainActivity", "onSensorChanged: INSIDE CIRCLE")
            }

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
                x = with(LocalDensity.current) { position.x.toDp() - Const.BALL_SIZE.dp / 2 },
                y = with(LocalDensity.current) { position.y.toDp() - Const.BALL_SIZE.dp / 2 })
            .size(Const.BALL_SIZE.dp)
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

@Composable
fun CircularTimeBar(
    remainingTime: State<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    totalTime: Int = Const.LEVEL_TIME
) {
    val remainingTimeState = remember { mutableStateOf(remainingTime.value) }

    LaunchedEffect(remainingTime.value) {
        while (remainingTimeState.value > 0) {
            delay(1000) // Wait for 1 second
            remainingTimeState.value -= 1
        }
    }

    Canvas(
        modifier = modifier
            .size(Const.TIMER_DIAMETER.dp) // Adjust the size as needed
        //.background(Color.Gray, CircleShape)
    ) {
        val sweepAngle = 360f * (remainingTimeState.value.toFloat() / totalTime)

        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(7.dp.toPx())
        )
    }
}


@Composable
fun CircularBar(
    remainingTime: Float,
    color: Color,
    modifier: Modifier = Modifier,
    totalTime: Float = Const.GOAL_TIME
) {
    if (remainingTime > 0f) {
        Canvas(
            modifier = modifier.size(Const.GOAL_DIAMETER.dp)
        ) {
            val sweepAngle = 360f * (remainingTime / totalTime)
            Log.d("goalBug", "CircularBar: sweepAngle -> $sweepAngle")

            // Apply offset to the drawing
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(7.dp.toPx()),
            )
        }
    } else {
        nextGoal = true
        goalRemainingTime = Const.GOAL_TIME + .05f
    }
}

@Composable
fun GoalCircle(remainingTime: Float, position: Offset, modifier: Modifier = Modifier) {
    // currentRemainingTime by remember { remainingTime }
    //Log.d("goalBug", "GoalCircle: currentRemainingTime -> $currentRemainingTime")
    CircularBar(
        remainingTime = remainingTime,
        color = Color.Yellow,
        modifier = modifier.size(Const.GOAL_DIAMETER.dp),
        totalTime = Const.GOAL_TIME
    )

    Log.d("MainActivity", "GoalCircle: I AM HERE")
}

@Composable
fun Goal(modifier: Modifier = Modifier) {
    if (nextGoal) {
        nextGoal = false

        randomX = kotlin.random.Random.nextInt(Const.GOAL_DIAMETER + Const.BALL_SIZE, screenWidth.toInt() - Const.GOAL_DIAMETER - Const.BALL_SIZE + 1).toFloat()
        randomY = kotlin.random.Random.nextInt(Const.GOAL_DIAMETER + Const.BALL_SIZE, screenHeight.toInt() - Const.GOAL_DIAMETER - Const.BALL_SIZE + 1).toFloat()

        GoalCircle(
            remainingTime = goalRemainingTime,
            position = Offset(randomX, randomY),
            modifier = modifier
                .absoluteOffset(
                    x = with(LocalDensity.current) { (randomX ).toDp() - (Const.GOAL_DIAMETER/2).dp },
                    y = with(LocalDensity.current) { (randomY ).toDp() - (Const.GOAL_DIAMETER/2).dp }
                )
        )
        currentGoal = Offset(randomX, randomY)
    } else {
        Log.d("goalBug", "Goal: goalRemainingTime -> $goalRemainingTime")
        GoalCircle(
            remainingTime = goalRemainingTime,
            position = Offset(randomX, randomY),
            modifier = modifier
                .absoluteOffset(
                    x = with(LocalDensity.current) { (randomX ).toDp() - (Const.GOAL_DIAMETER/2).dp },
                    y = with(LocalDensity.current) { (randomY ).toDp() - (Const.GOAL_DIAMETER/2).dp }
                )
        )
        currentGoal = Offset(randomX, randomY)
    }
}
