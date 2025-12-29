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
    private static final float KD_ANGLE = 0.6f;

    // 机体常量（与 QuadrotorEntity 中的常量保持一致以便混控）
    private static final float K_YAW = 0.2f; // yaw 力矩系数（与实体中一致）

    public MotorState Update(QuadrotorEntity quadrotor, ControlCommand command) {

        MotorState motorState = new MotorState();

        // 基准每电机推力（保持原有比例以稳定现有行为）
        float base = command.referenceThrottle * 0.5f; // 每电机基础推力
        float A = base * 4.0f; // 总推力（用于混控矩阵）

        // 读取当前姿态角（弧度）
        float pitch = quadrotor.getPitchAngle();
        float roll = quadrotor.getRollAngle();
        float yaw = quadrotor.getYawAngle();

        // --- YAW rate PID (控制偏航角速度) ---
        float currentYawRate = (yaw - lastYawAngle) / DT; // 近似角速度
        float yawErrorRate = command.referenceYawSpeed - currentYawRate;
        integralYawError += yawErrorRate * DT;
        // 防止积分发散（简单饱和）
        integralYawError = Math.max(-1.0f, Math.min(1.0f, integralYawError));
        float yawDerivative = (yawErrorRate - lastYawErrorRate) / DT;
        float yawControl = KP_YAW * yawErrorRate + KI_YAW * integralYawError + KD_YAW * yawDerivative;
        lastYawErrorRate = yawErrorRate;
        lastYawAngle = yaw;

        // --- PITCH angle PD (控制俯仰角) ---
        float pitchError = command.referencePitch - pitch;
        float pitchDerivative = (pitchError - lastPitchError) / DT;
        float pitchControl = KP_ANGLE * pitchError + KD_ANGLE * pitchDerivative;
        lastPitchError = pitchError;

        // --- ROLL angle PD (控制横滚角) ---
        float rollError = command.referenceRoll - roll;
        float rollDerivative = (rollError - lastRollError) / DT;
        float rollControl = KP_ANGLE * rollError + KD_ANGLE * rollDerivative;
        lastRollError = rollError;

        // 限幅以避免产生过大的力矩（可根据需要调整）
        float maxTorque = 2.0f; // 单位为与推力同标度的任意量
        pitchControl = Math.max(-maxTorque, Math.min(maxTorque, pitchControl));
        rollControl = Math.max(-maxTorque, Math.min(maxTorque, rollControl));
        float yawTorque = Math.max(-maxTorque, Math.min(maxTorque, yawControl));

        // --- Mixer 矩阵求解 (参考 QuadrotorEntity 的力矩定义)
        // 设：
        // A = t1 + t2 + t3 + t4
        // B = t1 + t2 - t3 - t4 = -2 * pitchTorque
        // C = t1 - t2 - t3 + t4 = 2 * rollTorque
        // D = t1 - t2 + t3 - t4 = yawTorque / K_YAW

        float B = -2.0f * pitchControl;
        float C =  2.0f * rollControl;
        float D =  yawTorque / K_YAW;

        float t1 = (A + B + C + D) / 4.0f;
        float t2 = (A + B - C - D) / 4.0f;
        float t3 = (A - B - C + D) / 4.0f;
        float t4 = (A - B + C - D) / 4.0f;

        // 将结果限制在 [-1, 1]，并施加一点阻尼（模拟电机响应）
        motorState.motor1 = Math.max(-1.0f, Math.min(1.0f, t1)) * 0.99f;
        motorState.motor2 = Math.max(-1.0f, Math.min(1.0f, t2)) * 0.99f;
        motorState.motor3 = Math.max(-1.0f, Math.min(1.0f, t3)) * 0.99f;
        motorState.motor4 = Math.max(-1.0f, Math.min(1.0f, t4)) * 0.99f;

        return motorState;
        
    }
}
