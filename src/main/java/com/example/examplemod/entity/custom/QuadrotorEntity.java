package com.example.examplemod.entity.custom;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

import org.joml.Math;

import com.example.examplemod.autopilot.AutoController;
import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.autopilot.autocontrollers.SimpleAutoController;
import com.example.examplemod.item.ModItems;
import com.example.examplemod.item.custom.RemoteController;
import com.mojang.authlib.GameProfile;

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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.world.ForgeChunkManager;
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
    private static final EntityDataAccessor<Float> DATA_ROLL_SPEED = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH_SPEED = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_YAW_SPEED = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUATERNION_W = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUATERNION_X = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUATERNION_Y = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QUATERNION_Z = SynchedEntityData.defineId(QuadrotorEntity.class, EntityDataSerializers.FLOAT);


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
    private Vector3f velocity = new Vector3f();
    private int debugTick = 0;

    // 角速度（机体轴系下）
    private Vector3f angularVelocityBody = new Vector3f();

    //四元数姿态
    public Quaternionf quaternion = new Quaternionf();
    private Quaternionf prevQuaternion = new Quaternionf();
    
    // 欧拉角（仅用于显示和同步，从四元数计算得到）
    private float rollAngle = 0.0f;  
    private float pitchAngle = 0.0f; 
    private float yawAngle = 0.0f;   

    // 物理参数
    private static final float MAX_THRUST = 0.7f;
    private static final double MASS = 1.0;
    private static final float INERTIA = 30; //原来是0.03
    private static final float K_YAW = 2.0f;
    private static final float ANGULAR_DAMPING = 0.3f;
    private static final double LINEAR_DRAG = 0.02;
    private static final double DT = 1.0 / 20.0;
    private static final double GRAVITY = 9.81 * 0.1;

    // 自动控制相关
    private final AutoController autoController = new SimpleAutoController();
    private ControlCommand controlCommand = new ControlCommand();

    private UUID pilotUUid = null;
    
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

        this.entityData.define(DATA_ROLL_SPEED, 0.0f);
        this.entityData.define(DATA_PITCH_SPEED, 0.0f);
        this.entityData.define(DATA_YAW_SPEED, 0.0f);

        this.entityData.define(DATA_QUATERNION_W, 1.0f);
        this.entityData.define(DATA_QUATERNION_X, 0.0f);
        this.entityData.define(DATA_QUATERNION_Y, 0.0f);
        this.entityData.define(DATA_QUATERNION_Z, 0.0f);
    }



    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);

        //服务端操作
        if(!this.level().isClientSide()){ 
            // if(this.pilotUUid != null){
            //     Player pilot = level().getPlayerByUUID(pilotUUid);
            //     pilot.teleportTo(this.getX(), this.getY() + 100, this.getZ());
            // }
            
            prevQuaternion.set(this.quaternion);

            // 应用自动控制
            MotorState autoMotorState = this.autoController.Update(this, this.controlCommand);
            this.setMotorState(autoMotorState);

            // 计算各电机的推力（标量）
            float t1 = this.motor1 * MAX_THRUST;
            float t2 = this.motor2 * MAX_THRUST;
            float t3 = this.motor3 * MAX_THRUST;
            float t4 = this.motor4 * MAX_THRUST;

            Vector3f accel = new Vector3f(0, t1 + t2 + t3 + t4, 0);
            accel.rotate(quaternion);
            accel.mul(0.03f);
            velocity.add(accel);
            velocity.add(0,-0.03f, 0);
            velocity.mul(0.98f);

            // 力矩计算（机体坐标系）
            float yawTorque = (-t1 + t2 - t3 + t4) * K_YAW; 
            float pitchTorque = (t3 + t4) - (t1 + t2);     
            float rollTorque =  (t1 + t4) - (t2 + t3);     
            
            // 机体坐标系的角加速度
            Vector3f angularAccelBody = new Vector3f(
                pitchTorque / INERTIA,
                yawTorque / INERTIA,
                rollTorque / INERTIA
            );

            // 计算机体轴系下新的角速度,记得应用阻尼
            angularVelocityBody.mul(1 - ANGULAR_DAMPING);
            angularVelocityBody = angularVelocityBody.add(angularAccelBody);
            

            // 该角速度会在这一游戏刻造成旋转，把这个旋转写成四元数的形式
            if (angularVelocityBody.lengthSquared() > 1.0E-7) {
                float angle = angularVelocityBody.length();
                Vector3f axis = new Vector3f(angularVelocityBody).normalize();
        
                Quaternionf delta_rotation = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, angle);
            
                // 计算该游戏刻的最终姿态：将之前的姿态和该旋转相乘
                quaternion = quaternion.mul(delta_rotation);
            }

            //四元数归一化，防止误差积累
            quaternion.normalize();

            // 更新欧拉角
            Vector3f eular = new Vector3f();
            quaternion.getEulerAnglesYXZ(eular);
            yawAngle = (float)eular.y;
            pitchAngle = (float)eular.x;
            rollAngle = (float)eular.z;

            this.setDeltaMovement(new Vec3(velocity));
            
            // 更新实体显示用的角度（度）
            this.setYRot(-(float)Math.toDegrees(yawAngle));
            this.setXRot((float)Math.toDegrees(pitchAngle));
            
            // 同步数据到客户端
            this.entityData.set(DATA_ROLL_ANGLE, rollAngle);
            this.entityData.set(DATA_YAW_ANGLE, yawAngle);
            this.entityData.set(DATA_PITCH_ANGLE, pitchAngle);
            this.syncQuaternion(quaternion);


            
            // 调试信息
            debugTick++;
            if (debugTick % 2 == 0) {
                this.setCustomName(Component.literal(String.format(
                    "velx=%.2f, vely=%.2f, velz=%.2f | motors=%.2f,%.2f,%.2f,%.2f",
                    velocity.x, velocity.y, velocity.z,
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

            this.prevQuaternion.set(this.quaternion);
            
            // 将服务端数据同步到客户端
            this.yawAngle = getSynchedYawAngle();
            this.pitchAngle = getSynchedPitchAngle();
            this.rollAngle = getSynchedRollAngle();
            this.quaternion = this.getSynchedQuaternion();
            
        }

        this.move(MoverType.SELF, this.getDeltaMovement());

        //防止在撞到方块后的速度累计
        if (!this.level().isClientSide()){
            this.velocity = new Vector3f(
                (float)this.getDeltaMovement().x, 
                (float)this.getDeltaMovement().y, 
                (float)this.getDeltaMovement().z
            );
        }

    }
    
    /**
     * 在电机处生成粒子效果（修改为使用四元数旋转）
     */
    private void spawnMotorParticle(Vec3 motorBodyPos, float motorThrust) {
        if (this.level().isClientSide() && Math.abs(motorThrust) > 0.05f) {
            
            
            //this.level().addParticle(ParticleTypes.FLAME, px, py, pz, vx, vy, vz);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Motor1", this.motor1);
        tag.putFloat("Motor2", this.motor2);
        tag.putFloat("Motor3", this.motor3);
        tag.putFloat("Motor4", this.motor4);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.motor1 = tag.getFloat("Motor1");
        this.motor2 = tag.getFloat("Motor2");
        this.motor3 = tag.getFloat("Motor3");
        this.motor4 = tag.getFloat("Motor4");
        
        // 读取角速度
        double avx = tag.getDouble("AngVelX");
        double avy = tag.getDouble("AngVelY");
        double avz = tag.getDouble("AngVelZ");
        
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

    /**
     * 将机体坐标系向量转换为世界坐标系。使用的旋转顺序为：roll(绕Z) -> pitch(绕X) -> yaw(绕Y)，角度均为弧度。
     */
    private Vec3 bodyToWorld(Vec3 v, double yaw, double pitch, double roll) {
        double x = v.x;
        double y = v.y;
        double z = v.z;

        double cr = Math.cos(roll);
        double sr = Math.sin(roll);
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);

        // roll around Z
        double x1 = x * cr - y * sr;
        double y1 = x * sr + y * cr;
        double z1 = z;
        // pitch around X
        double x2 = x1;
        double y2 = y1 * cp - z1 * sp;
        double z2 = y1 * sp + z1 * cp;
        // yaw around Y
        double x3 = x2 * cy - z2 * sy;
        double y3 = y2;
        double z3 = x2 * sy + z2 * cy;

        return new Vec3(x3, y3, z3);
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

    private float getSynchedYawAngle(){
        return this.entityData.get(DATA_YAW_ANGLE);
    }

    private float getSynchedPitchAngle(){
        return this.entityData.get(DATA_PITCH_ANGLE);
    }

    private float getSynchedRollAngle(){
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

    private float getSynchedYawSpeed(){
        return this.entityData.get(DATA_YAW_SPEED);
    }

    private float getSynchedPitchSpeed(){
        return this.entityData.get(DATA_PITCH_SPEED);
    }

    private float getSynchedRollSpeed(){
        return this.entityData.get(DATA_ROLL_SPEED);
    }

    public float getYawSpeed(){
        return (float)this.angularVelocityBody.y;
    }

    public float getPitchSpeed(){
        return (float)this.angularVelocityBody.x;
    }

    public float getRollSpeed(){
        return (float)this.angularVelocityBody.z;
    }

    public void setAngularVelocity(Vector3f angularVel){
        this.entityData.set(DATA_YAW_SPEED, angularVel.y);
        this.entityData.set(DATA_PITCH_SPEED, angularVel.x);
        this.entityData.set(DATA_ROLL_SPEED, angularVel.y);

        this.angularVelocityBody = angularVel;
    }

    private void syncQuaternion(Quaternionf quat){
        this.entityData.set(DATA_QUATERNION_W, quat.w);
        this.entityData.set(DATA_QUATERNION_X, quat.x);
        this.entityData.set(DATA_QUATERNION_Y, quat.y);
        this.entityData.set(DATA_QUATERNION_Z, quat.z);
    }

    private Quaternionf getSynchedQuaternion(){
        float w = this.entityData.get(DATA_QUATERNION_W);
        float x = this.entityData.get(DATA_QUATERNION_X);
        float y = this.entityData.get(DATA_QUATERNION_Y);
        float z = this.entityData.get(DATA_QUATERNION_Z);

        return new Quaternionf(x, y, z, w);
    }

    public Quaternionf getQuaternion(){
        return quaternion;
    }

    public Quaternionf getPrevQuaternion(){
        return prevQuaternion;
    }

    public void setPilotUUID(UUID uuid){
        this.pilotUUid = uuid;
    }
    




}