package com.example.examplemod.autopilot.autocontrollers;

import com.example.examplemod.autopilot.AutoController;
import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.entity.custom.QuadrotorEntity;

public class SimpleAutoController extends AutoController{
    
    public SimpleAutoController() {
        super();
    }

    // PID 状态（角度/角速率误差积分与上次误差，用于求导）
    private float integralYawError = 0.0f;
    private float lastYawAngle = 0.0f;
    private float lastYawErrorRate = 0.0f;

    private float lastPitchError = 0.0f;

    private float lastRollError = 0.0f;

    // PID 增益和常量
    private static final float DT = 1.0f / 20.0f; // tick 时间步（与实体中一致）

    // Yaw rate controller (desired yaw angular speed)
    private static final float KP_YAW = 1.0f;
    private static final float KI_YAW = 0.2f;
    private static final float KD_YAW = 0.02f;

    // Angle controllers for pitch & roll (angle -> torque)
    private static final float KP_ANGLE = 2.5f;
    private static final float KD_ANGLE = 0.6f; //原来是0.6

    // 机体常量（与 QuadrotorEntity 中的常量保持一致以便混控）
    private static final float K_YAW = 0.2f; // yaw 力矩系数（与实体中一致）

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
        float D =  command.referenceYaw / K_YAW;
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
