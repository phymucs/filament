/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.filament.utils

import android.view.MotionEvent
import android.view.View
import java.util.*

class GestureDetector(private val view: View, private val manipulator: Manipulator) {
    private enum class Gesture { NONE, ORBIT, PAN, ZOOM }

    // Simplified memento of MotionEvent, minimal but sufficient for our purposes.
    private data class TouchEvent(var pt0: Float2, var pt1: Float2, var count: Int) {
        constructor() : this(Float2(0f), Float2(0f), 0)
        constructor(me: MotionEvent) : this() {
            if (me.pointerCount >= 1) {
                this.pt0.x = me.getX(0)
                this.pt0.y = me.getY(0)
                this.count = 1
            }
            if (me.pointerCount >= 2) {
                this.pt1.x = me.getX(1)
                this.pt1.y = me.getY(1)
                this.count++
            }
        }
    }

    private var currentGesture = Gesture.NONE
    private var previousEvent = TouchEvent()
    private val tentativePanEvents = ArrayList<TouchEvent>()
    private val tentativeOrbitEvents = ArrayList<TouchEvent>()
    private val tentativeZoomEvents = ArrayList<TouchEvent>()

    private val kGestureConfidenceCount = 2
    private val kPanConfidenceDistance = 5
    private val kZoomConfidenceDistance = 10
    private val kZoomSpeed = 1f / 10f

    init {
        view.setOnTouchListener func@{ _, event ->
            val x = event.getX(0).toInt()
            val y = view.height - event.getY(0).toInt()
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {

                    // CANCEL GESTURE DUE TO UNEXPECTED POINTER COUNT

                    if ((event.pointerCount != 1 && currentGesture == Gesture.ORBIT) ||
                            (event.pointerCount != 2 && currentGesture == Gesture.PAN) ||
                            (event.pointerCount != 2 && currentGesture == Gesture.ZOOM)) {
                        endGesture()
                        return@func true
                    }

                    // UPDATE EXISTING GESTURE

                    if (currentGesture == Gesture.ZOOM) {
                        val d0 = getPinchDistance(previousEvent)
                        val d1 = getPinchDistance(TouchEvent(event))
                        manipulator.zoom(x, y, (d0 - d1) * kZoomSpeed)
                        previousEvent = TouchEvent(event)
                        return@func true
                    }

                    if (currentGesture != Gesture.NONE) {
                        manipulator.grabUpdate(x, y)
                        return@func true
                    }

                    // DETECT NEW GESTURE

                    if (event.pointerCount == 1) {
                        tentativeOrbitEvents.add(TouchEvent(event))
                    }

                    if (event.pointerCount == 2) {
                        tentativePanEvents.add(TouchEvent(event))
                        tentativeZoomEvents.add(TouchEvent(event))
                    }

                    if (isOrbitGesture()) {
                        manipulator.grabBegin(x, y, false)
                        currentGesture = Gesture.ORBIT
                        return@func true
                    }

                    if (isZoomGesture()) {
                        currentGesture = Gesture.ZOOM
                        previousEvent = TouchEvent(event)
                        return@func true
                    }

                    if (isPanGesture()) {
                        manipulator.grabBegin(x, y, true)
                        currentGesture = Gesture.PAN
                        return@func true
                    }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    endGesture()
                }
            }
            true
        }

    }

    private fun getPinchDistance(event: TouchEvent): Float { return distance(event.pt0, event.pt1) }
    private fun getTouchesMidpoint(event: TouchEvent): Float2 { return mix(event.pt0, event.pt1, 0.5f) }
    private fun isOrbitGesture(): Boolean { return tentativeOrbitEvents.size > kGestureConfidenceCount }

    private fun endGesture() {
        tentativePanEvents.clear()
        tentativeOrbitEvents.clear()
        tentativeZoomEvents.clear()
        currentGesture = Gesture.NONE
        manipulator.grabEnd()
    }

    private fun isPanGesture(): Boolean {
        if (tentativePanEvents.size <= kGestureConfidenceCount) {
            return false
        }
        val oldest = getTouchesMidpoint(tentativePanEvents.first())
        val newest = getTouchesMidpoint(tentativePanEvents.last())
        return distance(oldest, newest) > kPanConfidenceDistance
    }

    private fun isZoomGesture(): Boolean {
        if (tentativeZoomEvents.size <= kGestureConfidenceCount) {
            return false
        }
        val oldest = getPinchDistance(tentativeZoomEvents.first())
        val newest = getPinchDistance(tentativeZoomEvents.last())
        return kotlin.math.abs(newest - oldest) > kZoomConfidenceDistance
    }
}