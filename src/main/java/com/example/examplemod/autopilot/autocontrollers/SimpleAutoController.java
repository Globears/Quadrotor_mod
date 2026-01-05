package com.example.examplemod.autopilot.autocontrollers;

import org.joml.Vector3f;

import com.example.examplemod.autopilot.AutoController;
import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.entity.custom.QuadrotorEntity;

public class SimpleAutoController extends AutoController {
    
    // ------------------- 滚转速度PID参数 -------------------
    private final float ROLL_SPEED_KP = 2.0f;    
    private final float ROLL_SPEED_KI = 0.1f;    
    private final float ROLL_SPEED_KD = 0.05f;   
    private final float ROLL_INTEGRAL_LIMIT = 1.0f;  
    
    private float rollSpeedIntegral = 0.0f;      
    private float lastRollSpeedError = 0.0f;     

    // ------------------- 俯仰速度PID参数 -------------------
    private final float PITCH_SPEED_KP = 2.0f;   
    private final float PITCH_SPEED_KI = 0.1f;   
    private final float PITCH_SPEED_KD = 0.05f;  
    private final float PITCH_INTEGRAL_LIMIT = 1.0f; 
    
    private float pitchSpeedIntegral = 0.0f;     
    private float lastPitchSpeedError = 0.0f;    

    // ------------------- 自动偏航补偿优化参数 -------------------
    private final float ROLL_TO_YAW_KP = 0.5f;   
    private final float ROLL_DEAD_ZONE = 0.01f;  // 滚转角死区（归零判定）
    private final float YAW_COMP_LIMIT = 0.5f;    // 偏航补偿力矩上限（防止补偿过大）
    private final float YAW_DEAD_ZONE = 0.005f;   // 偏航力矩零死区（微小值直接归零）
    private final float YAW_DAMPING = 0.1f;       // 偏航角速度阻尼（抑制无指令偏航）

    // 通用时间戳
    private long lastUpdateTime = 0L;            

    public SimpleAutoController() {
        super();
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
        float yawSpeed = quadrotor.getAngularVelocity().y;    // 当前偏航角速度（新增）

        // 2. 计算时间差
        long currentTime = System.currentTimeMillis();
        float dt = Math.max(0.001f, (currentTime - lastUpdateTime) / 1000.0f);
        lastUpdateTime = currentTime;

        // 3. PID控制：滚转速度跟踪
        float rollSpeedError = command.referenceRoll - rollSpeed;
        float rollPTerm = ROLL_SPEED_KP * rollSpeedError;
        rollSpeedIntegral += rollSpeedError * dt * ROLL_SPEED_KI;
        rollSpeedIntegral = Math.max(-ROLL_INTEGRAL_LIMIT, Math.min(ROLL_INTEGRAL_LIMIT, rollSpeedIntegral));
        float rollITerm = rollSpeedIntegral;
        float rollDTerm = ROLL_SPEED_KD * (rollSpeedError - lastRollSpeedError) / dt;
        lastRollSpeedError = rollSpeedError;
        float rollTorque = Math.max(-2.0f, Math.min(2.0f, rollPTerm + rollITerm + rollDTerm));

        // 4. PID控制：俯仰速度跟踪
        float pitchSpeedError = command.referencePitch - pitchSpeed;
        float pitchPTerm = PITCH_SPEED_KP * pitchSpeedError;
        pitchSpeedIntegral += pitchSpeedError * dt * PITCH_SPEED_KI;
        pitchSpeedIntegral = Math.max(-PITCH_INTEGRAL_LIMIT, Math.min(PITCH_INTEGRAL_LIMIT, pitchSpeedIntegral));
        float pitchITerm = pitchSpeedIntegral;
        float pitchDTerm = PITCH_SPEED_KD * (pitchSpeedError - lastPitchSpeedError) / dt;
        lastPitchSpeedError = pitchSpeedError;
        float pitchTorque = Math.max(-2.0f, Math.min(2.0f, pitchPTerm + pitchITerm + pitchDTerm));

        // 5. 优化后的自动偏航补偿逻辑（核心修复）
        float yawCompensation = 0.0f;
        // 仅当滚转角超过死区时，才计算补偿力矩
        if (Math.abs(rollAngle) > ROLL_DEAD_ZONE) {
            yawCompensation = -ROLL_TO_YAW_KP * rollAngle;
            // 补偿力矩限幅，防止过大
            yawCompensation = Math.max(-YAW_COMP_LIMIT, Math.min(YAW_COMP_LIMIT, yawCompensation));
        } else {
            // 滚转角归零后，补偿力矩强制归零（消除残留）
            yawCompensation = 0.0f;
        }

        // 6. 无指令偏航抑制：叠加偏航角速度阻尼（阻尼力矩与偏航角速度反向）
        float yawDampingTorque = -YAW_DAMPING * yawSpeed;

        // 7. 最终偏航力矩计算
        float finalYawTorque = command.referenceYaw + yawCompensation + yawDampingTorque;
        // 偏航力矩零死区：微小值直接归零（核心修复无指令偏航）
        finalYawTorque = Math.abs(finalYawTorque) < YAW_DEAD_ZONE ? 0.0f : finalYawTorque;

        // 8. 混控矩阵求解
        float base = command.referenceThrottle * 0.5f;
        float A = base * 4.0f;
        float B = 2.0f * pitchTorque;
        float C = 2.0f * rollTorque;
        float D = 2.0f * finalYawTorque;

        float t1 = (A - B + C - D) / 4.0f;
        float t2 = (A - B - C + D) / 4.0f;
        float t3 = (A + B - C - D) / 4.0f;
        float t4 = (A + B + C + D) / 4.0f;

        // 9. 电机输出优化：微小值归零（消除浮点误差导致的偏航）
        float motorDeadZone = 0.005f; // 电机输出零死区
        motorState.motor1 = Math.abs(t1) < motorDeadZone ? 0.0f : Math.max(0.0f, Math.min(1.0f, t1)) * 0.99f;
        motorState.motor2 = Math.abs(t2) < motorDeadZone ? 0.0f : Math.max(0.0f, Math.min(1.0f, t2)) * 0.99f;
        motorState.motor3 = Math.abs(t3) < motorDeadZone ? 0.0f : Math.max(0.0f, Math.min(1.0f, t3)) * 0.99f;
        motorState.motor4 = Math.abs(t4) < motorDeadZone ? 0.0f : Math.max(0.0f, Math.min(1.0f, t4)) * 0.99f;

        return motorState;
    }

    // 重置所有PID状态
    public void resetPID() {
        this.rollSpeedIntegral = 0.0f;
        this.lastRollSpeedError = 0.0f;
        this.pitchSpeedIntegral = 0.0f;
        this.lastPitchSpeedError = 0.0f;
        this.lastUpdateTime = System.currentTimeMillis();
    }
}