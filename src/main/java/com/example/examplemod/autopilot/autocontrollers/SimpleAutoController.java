package com.example.examplemod.autopilot.autocontrollers;

import org.joml.Vector3f;

import com.example.examplemod.autopilot.AutoController;
import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.entity.custom.QuadrotorEntity;

public class SimpleAutoController extends AutoController{
    
    public SimpleAutoController() {
        super();
    }


    public MotorState Update(QuadrotorEntity quadrotor, ControlCommand command) {

        MotorState motorState = new MotorState();

        // 基准每电机推力（保持原有比例以稳定现有行为）
        float base = command.referenceThrottle * 0.5f; // 每电机基础推力
        float A = base * 4.0f; // 总推力（用于混控矩阵）


        // --- Mixer 矩阵求解 (参考 QuadrotorEntity 的力矩定义)
        // 设：
        // A =  t1 + t2 + t3 + t4
        // B = -t1 - t2 + t3 + t4 = 2 * pitchTorque
        // C =  t1 - t2 - t3 + t4 = 2 * rollTorque
        // D = -t1 + t2 - t3 + t4 = yawTorque / K_YAW

        float B =  2.0f * command.referencePitch;
        float C =  2.0f * command.referenceRoll;
        float D =  2.0f * command.referenceYaw;
        //我们约定，向左偏航为正的偏航角，偏航角速度和偏航力矩也以该方向为正
        // 无敌坐标系

        float t1 = (A - B + C - D) / 4.0f;
        float t2 = (A - B - C + D) / 4.0f;
        float t3 = (A + B - C - D) / 4.0f;
        float t4 = (A + B + C + D) / 4.0f;

        // 将结果限制在 [-1, 1]，并施加一点阻尼（模拟电机响应）
        motorState.motor1 = Math.max(0.0f, Math.min(1.0f, t1)) * 0.99f;
        motorState.motor2 = Math.max(0.0f, Math.min(1.0f, t2)) * 0.99f;
        motorState.motor3 = Math.max(0.0f, Math.min(1.0f, t3)) * 0.99f;
        motorState.motor4 = Math.max(0.0f, Math.min(1.0f, t4)) * 0.99f;

        return motorState;
        
    }
}