package com.example.examplemod.autopilot.autocontrollers;

import org.joml.Vector3f;

import com.example.examplemod.autopilot.AutoController;
import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.entity.custom.QuadrotorEntity;

public class SimpleAutoController extends AutoController {
    
    // ------------------- 滚转速度PID参数 -------------------
    private final float ROLL_SPEED_KP = 2.0f;    // 比例系数
    private final float ROLL_SPEED_KI = 0.1f;    // 积分系数
    private final float ROLL_SPEED_KD = 0.05f;   // 微分系数
    private final float ROLL_INTEGRAL_LIMIT = 1.0f;  // 积分限幅
    
    // 滚转PID状态变量
    private float rollSpeedIntegral = 0.0f;      
    private float lastRollSpeedError = 0.0f;     

    // ------------------- 俯仰速度PID参数 -------------------
    private final float PITCH_SPEED_KP = 2.0f;   // 比例系数（可独立调参）
    private final float PITCH_SPEED_KI = 0.1f;   // 积分系数
    private final float PITCH_SPEED_KD = 0.05f;  // 微分系数
    private final float PITCH_INTEGRAL_LIMIT = 1.0f; // 积分限幅
    
    // 俯仰PID状态变量
    private float pitchSpeedIntegral = 0.0f;     
    private float lastPitchSpeedError = 0.0f;    

    // ------------------- 自动偏航补偿参数 -------------------
    private final float ROLL_TO_YAW_KP = 0.5f;   
    private final float ROLL_DEAD_ZONE = 0.01f;  

    // 通用时间戳（统一计算dt）
    private long lastUpdateTime = 0L;            

    public SimpleAutoController() {
        super();
        // 初始化所有PID状态变量
        this.rollSpeedIntegral = 0.0f;
        this.lastRollSpeedError = 0.0f;
        this.pitchSpeedIntegral = 0.0f;
        this.lastPitchSpeedError = 0.0f;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public MotorState Update(QuadrotorEntity quadrotor, ControlCommand command) {
        MotorState motorState = new MotorState();

        // 1. 获取四旋翼当前状态
        Vector3f eular = new Vector3f();
        quadrotor.getQuaternion().getEulerAnglesYXZ(eular);
        float rollAngle = (float) eular.z;  // 当前滚转角
        float pitchSpeed = quadrotor.getAngularVelocity().x;  // 当前俯仰速度
        float rollSpeed = quadrotor.getAngularVelocity().z;   // 当前滚转速度

        // 2. 计算时间差（统一用dt，避免多次获取时间）
        long currentTime = System.currentTimeMillis();
        float dt = Math.max(0.001f, (currentTime - lastUpdateTime) / 1000.0f);  // 避免dt为0
        lastUpdateTime = currentTime;

        // 3. PID控制：滚转速度跟踪
        float rollSpeedError = command.referenceRoll - rollSpeed;
        float rollPTerm = ROLL_SPEED_KP * rollSpeedError;
        // 积分项（带限幅）
        rollSpeedIntegral += rollSpeedError * dt * ROLL_SPEED_KI;
        rollSpeedIntegral = Math.max(-ROLL_INTEGRAL_LIMIT, Math.min(ROLL_INTEGRAL_LIMIT, rollSpeedIntegral));
        float rollITerm = rollSpeedIntegral;
        // 微分项
        float rollDTerm = ROLL_SPEED_KD * (rollSpeedError - lastRollSpeedError) / dt;
        lastRollSpeedError = rollSpeedError;
        // 最终滚转力矩（限幅）
        float rollTorque = Math.max(-2.0f, Math.min(2.0f, rollPTerm + rollITerm + rollDTerm));

        // 4. PID控制：俯仰速度跟踪（逻辑与滚转完全一致，仅参数/状态变量不同）
        float pitchSpeedError = command.referencePitch - pitchSpeed;
        float pitchPTerm = PITCH_SPEED_KP * pitchSpeedError;
        // 积分项（带限幅）
        pitchSpeedIntegral += pitchSpeedError * dt * PITCH_SPEED_KI;
        pitchSpeedIntegral = Math.max(-PITCH_INTEGRAL_LIMIT, Math.min(PITCH_INTEGRAL_LIMIT, pitchSpeedIntegral));
        float pitchITerm = pitchSpeedIntegral;
        // 微分项
        float pitchDTerm = PITCH_SPEED_KD * (pitchSpeedError - lastPitchSpeedError) / dt;
        lastPitchSpeedError = pitchSpeedError;
        // 最终俯仰力矩（限幅）
        float pitchTorque = Math.max(-2.0f, Math.min(2.0f, pitchPTerm + pitchITerm + pitchDTerm));

        // 5. 自动偏航补偿：滚转角归零
        float yawCompensation = 0.0f;
        if (Math.abs(rollAngle) > ROLL_DEAD_ZONE) {
            yawCompensation = -ROLL_TO_YAW_KP * rollAngle;
        }
        float finalYawTorque = command.referenceYaw + yawCompensation;

        // 6. 混控矩阵求解（俯仰/滚转力矩均替换为PID输出）
        float base = command.referenceThrottle * 0.5f;
        float A = base * 4.0f;  // 总推力
        float B = 2.0f * pitchTorque;       // 俯仰力矩（PID输出）
        float C = 2.0f * rollTorque;        // 滚转力矩（PID输出）
        float D = 2.0f * finalYawTorque;    // 偏航力矩（叠加自动补偿）

        // 解混控矩阵，计算每个电机的推力
        float t1 = (A - B + C - D) / 4.0f;
        float t2 = (A - B - C + D) / 4.0f;
        float t3 = (A + B - C - D) / 4.0f;
        float t4 = (A + B + C + D) / 4.0f;

        // 限制电机输出范围 [0,1]，添加轻微阻尼
        motorState.motor1 = Math.max(0.0f, Math.min(1.0f, t1)) * 0.99f;
        motorState.motor2 = Math.max(0.0f, Math.min(1.0f, t2)) * 0.99f;
        motorState.motor3 = Math.max(0.0f, Math.min(1.0f, t3)) * 0.99f;
        motorState.motor4 = Math.max(0.0f, Math.min(1.0f, t4)) * 0.99f;

        return motorState;
    }

    // 重置所有PID状态（四旋翼重启/复位时调用）
    public void resetPID() {
        this.rollSpeedIntegral = 0.0f;
        this.lastRollSpeedError = 0.0f;
        this.pitchSpeedIntegral = 0.0f;
        this.lastPitchSpeedError = 0.0f;
        this.lastUpdateTime = System.currentTimeMillis();
    }
}