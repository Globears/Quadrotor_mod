package com.example.examplemod.autopilot;

import com.example.examplemod.entity.custom.QuadrotorEntity;

public class AutoController {

    public AutoController() {
    }

    public MotorState Update(QuadrotorEntity quadrotor, ControlCommand command) {
        // 基类不实现具体控制逻辑，留给子类实现
        return new MotorState();
    }
}
