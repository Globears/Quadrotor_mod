package com.example.examplemod.autopilot;

public class MotorState {
    //电机状态，作为一个数据集合来传输
    public float motor1;
    public float motor2;
    public float motor3;
    public float motor4;

    public MotorState() {
        this.motor1 = 0.0f;
        this.motor2 = 0.0f;
        this.motor3 = 0.0f;
        this.motor4 = 0.0f;
    }
}
