package com.example.examplemod.entity.custom;

import com.example.examplemod.autopilot.AutoController;
import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.autopilot.autocontrollers.SimpleAutoController;
import com.example.examplemod.item.ModItems;
import com.example.examplemod.item.RemoteController;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraftforge.network.NetworkHooks; 

public class QuadrotorEntity extends Entity {

    // Synced motor thrust values (synced to clients)
    private static final EntityDataAccessor<Float> DATA_MOTOR1 = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MOTOR2 = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MOTOR3 = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MOTOR4 = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL_ANGLE = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH_ANGLE = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_YAW_ANGLE = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);

    // 推力控制变量
    private float motor1 = 0.0f;   // 电机1推力
    private float motor2 = 0.0f;   // 电机2推力
    private float motor3 = 0.0f;   // 电机3推力
    private float motor4 = 0.0f;   // 电机4推力 

    //电机位置
    private final Vec3 motor1Pos = new Vec3(0.5, 0, 0.5);//左上角
    private final Vec3 motor2Pos = new Vec3(-0.5, 0, 0.5);//右上角
    private final Vec3 motor3Pos = new Vec3(-0.5, 0, -0.5);//右下角
    private final Vec3 motor4Pos = new Vec3(0.5, 0, -0.5);//左下角

    // 运动状态
    private Vec3 velocity = Vec3.ZERO;
    private int debugTick = 0;

    // 角运动状态（在机体坐标系里）
    private Vec3 angularVelocityBody = Vec3.ZERO; // (p, q, r) 对应绕 X（横滚）、Y（俯仰）、Z（偏航）的角速度，单位：rad/s
    
    // 四元数姿态表示（w, x, y, z）
    private double quaternionW = 1.0;  // 实部
    private double quaternionX = 0.0;  // i分量
    private double quaternionY = 0.0;  // j分量
    private double quaternionZ = 0.0;  // k分量
    
    // 欧拉角（仅用于显示和同步，从四元数计算得到）
    private float rollAngle = 0.0f;   // 绕 X 轴
    private float pitchAngle = 0.0f;  // 绕 Y 轴
    private float yawAngle = 0.0f;    // 绕 Z 轴

    // 物理参数
    private static final double MAX_THRUST = 0.7;
    private static final double MASS = 1.0;
    private static final double INERTIA = 0.03;
    private static final double K_YAW = 0.2;
    private static final double ANGULAR_DAMPING = 0.2;
    private static final double LINEAR_DRAG = 0.02;
    private static final double DT = 1.0 / 20.0;
    private static final double GRAVITY = 9.81 * 0.1;

    private final AutoController autoController = new SimpleAutoController();
    private ControlCommand controlCommand = new ControlCommand();

    public QuadrotorEntity(EntityType<? extends QuadrotorEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_MOTOR1, 0.0f);
        this.entityData.define(DATA_MOTOR2, 0.0f);
        this.entityData.define(DATA_MOTOR3, 0.0f);
        this.entityData.define(DATA_MOTOR4, 0.0f);

        this.entityData.define(DATA_ROLL_ANGLE, 0.0f);
        this.entityData.define(DATA_PITCH_ANGLE, 0.0f);
        this.entityData.define(DATA_YAW_ANGLE, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);

        //服务端操作
        if(!this.level().isClientSide()){ 
            // 应用自动控制
            MotorState autoMotorState = this.autoController.Update(this, this.controlCommand);
            this.setMotorState(autoMotorState);

            // 计算总推力和力矩（机体坐标系）
            double t1 = this.motor1 * MAX_THRUST;
            double t2 = this.motor2 * MAX_THRUST;
            double t3 = this.motor3 * MAX_THRUST;
            double t4 = this.motor4 * MAX_THRUST;

            double totalThrust = t1 + t2 + t3 + t4;

            // 力矩计算（机体坐标系）
            double rollTorque = (t2 + t3) - (t1 + t4);      // 横滚力矩（绕X轴）
            double pitchTorque = (t1 + t2) - (t3 + t4);     // 俯仰力矩（绕Y轴）
            double yawTorque = (-t1 + t2 - t3 + t4) * K_YAW; // 偏航力矩（绕Z轴）

            // 机体坐标系的角加速度
            Vec3 angularAccelBody = new Vec3(
                rollTorque / INERTIA,
                pitchTorque / INERTIA,
                yawTorque / INERTIA
            );

            // 在机体坐标系中更新角速度
            angularVelocityBody = angularVelocityBody.add(angularAccelBody.scale(DT));
            angularVelocityBody = angularVelocityBody.scale(1.0 - ANGULAR_DAMPING);

            // 使用四元数更新姿态
            updateQuaternionFromAngularVelocity(DT);

            // 计算世界坐标系的推力（使用四元数旋转）
            Vec3 thrustBody = new Vec3(0, totalThrust, 0);
            Vec3 thrustWorld = rotateVectorByQuaternion(thrustBody);
            
            // 计算加速度（包括重力）
            Vec3 accel = thrustWorld.scale(1.0 / MASS);
            accel = accel.add(0, -GRAVITY, 0);
            
            // 更新速度
            velocity = velocity.add(accel.scale(DT));
            velocity = velocity.scale(1.0 - LINEAR_DRAG);
            
            // 清除Z轴速度（你之前有velocity = Vec3.ZERO; 但我认为这不是你想要的）
            // 如果不需要固定速度，请注释掉下面这行
            velocity = Vec3.ZERO;

            // 防止在地面上时向下的速度累计
            if (this.onGround() && velocity.y < 0) {
                velocity = new Vec3(velocity.x, 0, velocity.z);
            }

            this.setDeltaMovement(velocity);
            
            // 从四元数计算欧拉角（用于显示和同步）
            calculateEulerAnglesFromQuaternion();
            
            // 更新实体显示用的角度（度）
            this.setYRot((float)Math.toDegrees(yawAngle));
            this.setXRot((float)Math.toDegrees(pitchAngle));
            
            // 同步数据到客户端
            this.entityData.set(DATA_ROLL_ANGLE, rollAngle);
            this.entityData.set(DATA_YAW_ANGLE, yawAngle);
            this.entityData.set(DATA_PITCH_ANGLE, pitchAngle);
            
            // 调试信息
            debugTick++;
            if (debugTick % 2 == 0) {
                this.setCustomName(Component.literal(String.format(
                    "yaw %.2f,pitch %.2f,roll %.2f | motors=%.2f,%.2f,%.2f,%.2f",
                    yawAngle, pitchAngle, rollAngle,
                    motor1, motor2, motor3, motor4
                )));
                this.setCustomNameVisible(true);
            }
        }
        
        // 客户端操作：可视化电机推力
        if (this.level().isClientSide()) {
            spawnMotorParticle(motor1Pos, this.entityData.get(DATA_MOTOR1));
            spawnMotorParticle(motor2Pos, this.entityData.get(DATA_MOTOR2));
            spawnMotorParticle(motor3Pos, this.entityData.get(DATA_MOTOR3));
            spawnMotorParticle(motor4Pos, this.entityData.get(DATA_MOTOR4));
            
            // 从同步数据获取欧拉角（客户端显示用）
            this.yawAngle = getSynchedYawAngle();
            this.pitchAngle = getSynchedPitchAngle();
            this.rollAngle = getSynchedRollAngle();
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
    }
    
    /**
     * 使用四元数更新姿态
     * 根据机体坐标系下的角速度更新四元数
     */
    private void updateQuaternionFromAngularVelocity(double dt) {
        double p = angularVelocityBody.x; // 横滚角速度
        double q = angularVelocityBody.y; // 俯仰角速度
        double r = angularVelocityBody.z; // 偏航角速度
        
        // 计算旋转向量（机体坐标系）
        double angle = Math.sqrt(p*p + q*q + r*r) * dt;
        
        if (angle < 1e-12) {
            return; // 无旋转
        }
        
        // 计算旋转轴（机体坐标系）
        double axisX = p / Math.sqrt(p*p + q*q + r*r);
        double axisY = q / Math.sqrt(p*p + q*q + r*r);
        double axisZ = r / Math.sqrt(p*p + q*q + r*r);
        
        // 构造旋转四元数
        double sinHalfAngle = Math.sin(angle / 2.0);
        double cosHalfAngle = Math.cos(angle / 2.0);
        
        double deltaQW = cosHalfAngle;
        double deltaQX = axisX * sinHalfAngle;
        double deltaQY = axisY * sinHalfAngle;
        double deltaQZ = axisZ * sinHalfAngle;
        
        // 使用四元数乘法更新姿态：q_new = q_old * delta_q
        // 注意：这里使用右乘，因为旋转是在机体坐标系中发生的
        double w = quaternionW;
        double x = quaternionX;
        double y = quaternionY;
        double z = quaternionZ;
        
        quaternionW = w*deltaQW - x*deltaQX - y*deltaQY - z*deltaQZ;
        quaternionX = w*deltaQX + x*deltaQW + y*deltaQZ - z*deltaQY;
        quaternionY = w*deltaQY - x*deltaQZ + y*deltaQW + z*deltaQX;
        quaternionZ = w*deltaQZ + x*deltaQY - y*deltaQX + z*deltaQW;
        
        // 归一化四元数（防止数值误差积累）
        normalizeQuaternion();
    }
    
    /**
     * 归一化四元数
     */
    private void normalizeQuaternion() {
        double norm = Math.sqrt(
            quaternionW*quaternionW + 
            quaternionX*quaternionX + 
            quaternionY*quaternionY + 
            quaternionZ*quaternionZ
        );
        
        if (norm > 0.0) {
            quaternionW /= norm;
            quaternionX /= norm;
            quaternionY /= norm;
            quaternionZ /= norm;
        } else {
            // 如果四元数为零，重置为单位四元数
            quaternionW = 1.0;
            quaternionX = 0.0;
            quaternionY = 0.0;
            quaternionZ = 0.0;
        }
    }
    
    /**
     * 使用四元数旋转向量（机体坐标系 -> 世界坐标系）
     */
    private Vec3 rotateVectorByQuaternion(Vec3 v) {
        // 提取四元数分量
        double qw = quaternionW;
        double qx = quaternionX;
        double qy = quaternionY;
        double qz = quaternionZ;
        
        // 提取向量分量
        double vx = v.x;
        double vy = v.y;
        double vz = v.z;
        
        // 计算四元数旋转：v' = q * v * q_conj
        // 其中v是纯四元数 (0, vx, vy, vz)
        double tx = 2.0 * (qy * vz - qz * vy);
        double ty = 2.0 * (qz * vx - qx * vz);
        double tz = 2.0 * (qx * vy - qy * vx);
        
        double rx = vx + qw * tx + (qy * tz - qz * ty);
        double ry = vy + qw * ty + (qz * tx - qx * tz);
        double rz = vz + qw * tz + (qx * ty - qy * tx);
        
        return new Vec3(rx, ry, rz);
    }
    
    /**
     * 从四元数计算欧拉角（Z-Y-X顺序：偏航->俯仰->横滚）
     */
    private void calculateEulerAnglesFromQuaternion() {
        double qw = quaternionW;
        double qx = quaternionX;
        double qy = quaternionY;
        double qz = quaternionZ;
        
        // 使用Z-Y-X顺序（偏航->俯仰->横滚）
        // 这是标准的航空航天顺序
        
        // 横滚 (x轴旋转)
        double sinr_cosp = 2.0 * (qw * qx + qy * qz);
        double cosr_cosp = 1.0 - 2.0 * (qx * qx + qy * qy);
        rollAngle = (float) Math.atan2(sinr_cosp, cosr_cosp);
        
        // 俯仰 (y轴旋转)
        double sinp = 2.0 * (qw * qy - qz * qx);
        if (Math.abs(sinp) >= 1) {
            pitchAngle = (float) Math.copySign(Math.PI / 2, sinp);
        } else {
            pitchAngle = (float) Math.asin(sinp);
        }
        
        // 偏航 (z轴旋转)
        double siny_cosp = 2.0 * (qw * qz + qx * qy);
        double cosy_cosp = 1.0 - 2.0 * (qy * qy + qz * qz);
        yawAngle = (float) Math.atan2(siny_cosp, cosy_cosp);
        
        // 归一化角度到 [-π, π]
        rollAngle = (float) normalizeAngle(rollAngle);
        pitchAngle = (float) normalizeAngle(pitchAngle);
        yawAngle = (float) normalizeAngle(yawAngle);
    }
    
    /**
     * 角度归一化到 [-π, π]
     */
    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2.0 * Math.PI;
        while (angle < -Math.PI) angle += 2.0 * Math.PI;
        return angle;
    }
    
    /**
     * 在电机处生成粒子效果（修改为使用四元数旋转）
     */
    private void spawnMotorParticle(Vec3 motorBodyPos, float motorThrust) {
        if (this.level().isClientSide() && Math.abs(motorThrust) > 0.05f) {
            // 使用四元数旋转电机位置到世界坐标系
            Vec3 offset = rotateVectorByQuaternion(motorBodyPos);
            double px = this.getX() + offset.x;
            double py = this.getY() + offset.y;
            double pz = this.getZ() + offset.z;
            
            // 推力方向（机体 +Y 方向）
            Vec3 thrustDir = rotateVectorByQuaternion(new Vec3(0, 1, 0)).normalize();
            
            // 粒子速度与推力大小相关
            double speed = 0.1 + 0.4 * Math.abs(motorThrust);
            double vx = -thrustDir.x * speed;
            double vy = -thrustDir.y * speed;
            double vz = -thrustDir.z * speed;
            
            this.level().addParticle(ParticleTypes.FLAME, px, py, pz, vx, vy, vz);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Motor1", this.motor1);
        tag.putFloat("Motor2", this.motor2);
        tag.putFloat("Motor3", this.motor3);
        tag.putFloat("Motor4", this.motor4);
        
        // 保存四元数
        tag.putDouble("QuatW", quaternionW);
        tag.putDouble("QuatX", quaternionX);
        tag.putDouble("QuatY", quaternionY);
        tag.putDouble("QuatZ", quaternionZ);
        
        // 保存角速度
        tag.putDouble("AngVelX", angularVelocityBody.x);
        tag.putDouble("AngVelY", angularVelocityBody.y);
        tag.putDouble("AngVelZ", angularVelocityBody.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.motor1 = tag.getFloat("Motor1");
        this.motor2 = tag.getFloat("Motor2");
        this.motor3 = tag.getFloat("Motor3");
        this.motor4 = tag.getFloat("Motor4");
        
        // 读取四元数
        quaternionW = tag.getDouble("QuatW");
        quaternionX = tag.getDouble("QuatX");
        quaternionY = tag.getDouble("QuatY");
        quaternionZ = tag.getDouble("QuatZ");
        
        // 读取角速度
        double avx = tag.getDouble("AngVelX");
        double avy = tag.getDouble("AngVelY");
        double avz = tag.getDouble("AngVelZ");
        angularVelocityBody = new Vec3(avx, avy, avz);
        
        // 重新计算欧拉角
        calculateEulerAnglesFromQuaternion();
        
        // 同步数据
        this.entityData.set(DATA_MOTOR1, this.motor1);
        this.entityData.set(DATA_MOTOR2, this.motor2);
        this.entityData.set(DATA_MOTOR3, this.motor3);
        this.entityData.set(DATA_MOTOR4, this.motor4);
        
        this.entityData.set(DATA_YAW_ANGLE, yawAngle);
        this.entityData.set(DATA_PITCH_ANGLE, pitchAngle);
        this.entityData.set(DATA_ROLL_ANGLE, rollAngle);
    }

    // ... 其他方法保持不变 ...
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            player.displayClientMessage(Component.literal("Quadrotor: 交互 (客户端)"), false);
            return InteractionResult.SUCCESS;
        }

        ItemStack item = player.getMainHandItem();
        if(item.getItem() == ModItems.RemoteController.get()){
            int entityId = this.getId();
            RemoteController.setPairedQuadrotorId(item, entityId);
        }

        player.displayClientMessage(Component.literal("Quadrotor: 交互 (服务器)"), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable(){
        return true;
    }

    @Override
    public boolean canBeCollidedWith(){
        return true;
    }

    public void setCommand(ControlCommand command) {
        this.controlCommand = command;
    }

    public void setMotorState(float motor1, float motor2, float motor3, float motor4){
        this.motor1 = motor1;
        this.motor2 = motor2;
        this.motor3 = motor3;
        this.motor4 = motor4;
    }

    public void setMotorState(MotorState motorState){
        setMotorState(motorState.motor1, motorState.motor2, motorState.motor3, motorState.motor4);
    }

    public float getSynchedYawAngle(){
        return this.entityData.get(DATA_YAW_ANGLE);
    }

    public float getSynchedPitchAngle(){
        return this.entityData.get(DATA_PITCH_ANGLE);
    }

    public float getSynchedRollAngle(){
        return this.entityData.get(DATA_ROLL_ANGLE);
    }

    public float getYawAngle() {
        return this.yawAngle;
    }

    public float getPitchAngle(){
        return this.pitchAngle;
    }

    public float getRollAngle() {
        return this.rollAngle;
    }
}