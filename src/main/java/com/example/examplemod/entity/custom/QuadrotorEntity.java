package com.example.examplemod.entity.custom;

import java.util.UUID;

import com.example.examplemod.autopilot.AutoController;
import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.autopilot.autocontrollers.SimpleAutoController;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraftforge.network.NetworkHooks; 

public class QuadrotorEntity extends Entity {
    // controller player UUID
    private UUID controllerUuid = null;

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
    private final Vec3 motor1Pos = new Vec3(0.5, 0, 0.5);
    private final Vec3 motor2Pos = new Vec3(-0.5, 0, 0.5);
    private final Vec3 motor3Pos = new Vec3(-0.5, 0, -0.5);
    private final Vec3 motor4Pos = new Vec3(0.5, 0, -0.5);

    // 运动状态
    private Vec3 velocity = Vec3.ZERO;
    private int debugTick = 0;

    // 姿态 / 角运动状态（在机体坐标系里）
    private Vec3 angularVelocity = Vec3.ZERO; // (omega_x, omega_y, omega_z) 对应绕 X（侧滚/俯仰）、Y（偏航）、Z（纵滚/横滚）的角速度，单位：rad/s
    private float pitchAngle = 0.0f; // 绕 X 轴 (rad)
    private float yawAngle = 0.0f;   // 绕 Y 轴 (rad)
    private float rollAngle = 0.0f;  // 绕 Z 轴 (rad)

    // 物理参数（可调试以获得合适的行为）
    private static final double MAX_THRUST = 0.7; // 每个电机最大推力（游戏/物理单位，需调参）
    private static final double MASS = 1.0;       // 无人机质量（单位）
    private static final double INERTIA = 0.03;   // 转动惯量（简化为标量）
    private static final double K_YAW = 0.2;      // 由电机自旋产生的偏航力矩系数
    private static final double ANGULAR_DAMPING = 3; // 角阻尼系数
    private static final double LINEAR_DRAG = 0.02;    // 线阻尼（每 tick 的缩放量）
    private static final double DT = 1.0 / 20.0;      // tick 时步（秒）
    private static final double GRAVITY = 9.81 * 0.1; // 重力缩放以匹配之前近似的 -0.03/tick

    private final AutoController autoController = new SimpleAutoController();
    private ControlCommand controlCommand = new ControlCommand();


    public QuadrotorEntity(EntityType<? extends QuadrotorEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        //同步电机推力的数据
        this.entityData.define(DATA_MOTOR1, 0.0f);
        this.entityData.define(DATA_MOTOR2, 0.0f);
        this.entityData.define(DATA_MOTOR3, 0.0f);
        this.entityData.define(DATA_MOTOR4, 0.0f);

        this.entityData.define(DATA_ROLL_ANGLE, 0.0f);
        this.entityData.define(DATA_PITCH_ANGLE, 0.0f);
        this.entityData.define(DATA_YAW_ANGLE, 0.0f);
        
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    public UUID getController() { 
        return this.controllerUuid; 
    }

    // 绑定控制者（服务器端调用）
    public void bindController(UUID uuid) {
        if (this.level().isClientSide()) return;
        this.controllerUuid = uuid;
    }

    // 解除绑定（服务器端调用）
    public void unbindController() {
        if (this.level().isClientSide()) return;
        this.controllerUuid = null;
    }
    
    // 推力控制方法（从网络包调用 或 在得到自动控制器的控制量后自己调整）
    public void setMotorState(float motor1, float motor2, float motor3, float motor4) {
        if (this.level().isClientSide()) return;
        
        this.motor1 = Mth.clamp(motor1, -1f, 1f);
        this.motor2 = Mth.clamp(motor2, -1f, 1f);
        this.motor3 = Mth.clamp(motor3, -1f, 1f);
        this.motor4 = Mth.clamp(motor4, -1f, 1f);

        // 更新同步数据，让客户端看到电机推力
        this.entityData.set(DATA_MOTOR1, this.motor1);
        this.entityData.set(DATA_MOTOR2, this.motor2);
        this.entityData.set(DATA_MOTOR3, this.motor3);
        this.entityData.set(DATA_MOTOR4, this.motor4);
    }

    public void setMotorState(MotorState motorState) {
        setMotorState(motorState.motor1, motorState.motor2, motorState.motor3, motorState.motor4);
    }
    
    public MotorState getMotorState() {
        MotorState state = new MotorState();
        state.motor1 = this.motor1;
        state.motor2 = this.motor2;
        state.motor3 = this.motor3;
        state.motor4 = this.motor4;
        return state;
    }
    

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (controllerUuid != null) {
            tag.putUUID("ControllerRemote", controllerUuid);
        }
        tag.putFloat("Motor1", this.motor1);
        tag.putFloat("Motor2", this.motor2);
        tag.putFloat("Motor3", this.motor3);
        tag.putFloat("Motor4", this.motor4);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("ControllerRemote")) {
            this.controllerUuid = tag.getUUID("ControllerRemote");
        }
        this.motor1 = tag.getFloat("Motor1");
        this.motor2 = tag.getFloat("Motor2");
        this.motor3 = tag.getFloat("Motor3");
        this.motor4 = tag.getFloat("Motor4");

        // 将存档的推力值写入同步数据（客户端加载实体时可见）
        this.entityData.set(DATA_MOTOR1, this.motor1);
        this.entityData.set(DATA_MOTOR2, this.motor2);
        this.entityData.set(DATA_MOTOR3, this.motor3);
        this.entityData.set(DATA_MOTOR4, this.motor4);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            player.displayClientMessage(Component.literal("Quadrotor: 交互 (客户端)"), false);
            return InteractionResult.sidedSuccess(true);
        }

        player.displayClientMessage(Component.literal("Quadrotor: 交互 (服务器)"), false);

        // 从玩家手中获取遥控器的 UUID（优先主手）
        java.util.UUID remoteId = null;
        net.minecraft.world.item.ItemStack main = player.getMainHandItem();
        net.minecraft.world.item.ItemStack off = player.getOffhandItem();
        if (main.getItem() == com.example.examplemod.item.ModItems.RemoteController.get()) {
            remoteId = com.example.examplemod.item.RemoteController.getOrCreateRemoteId(main);
        } else if (off.getItem() == com.example.examplemod.item.ModItems.RemoteController.get()) {
            remoteId = com.example.examplemod.item.RemoteController.getOrCreateRemoteId(off);
        }

        if (remoteId == null) {
            player.displayClientMessage(Component.literal("Quadrotor: 请手持遥控器与无人机配对"), false);
            return InteractionResult.sidedSuccess(false);
        }

        if (player.isShiftKeyDown()) {
            // 解绑（仅当绑定到该遥控器时）
            if (controllerUuid != null && controllerUuid.equals(remoteId)) {
                controllerUuid = null;
                player.displayClientMessage(Component.literal("Quadrotor: 已解绑"), false);
            } else {
                player.displayClientMessage(Component.literal("Quadrotor: 未绑定给你手持的遥控器"), false);
            }
        } else {
            // 绑定到手持遥控器
            if (controllerUuid == null) {
                controllerUuid = remoteId;
                player.displayClientMessage(Component.literal("Quadrotor: 已绑定给该遥控器"), false);
            } else if (controllerUuid.equals(remoteId)) {
                player.displayClientMessage(Component.literal("Quadrotor: 已经绑定给该遥控器"), false);
            } else {
                player.displayClientMessage(Component.literal("Quadrotor: 已经绑定给其他遥控器"), false);
            }
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);

        //服务端操作
        if(!this.level().isClientSide()){

            //应用自动控制
            MotorState autoMotorState = this.autoController.Update(this, this.controlCommand);
            this.setMotorState(autoMotorState.motor1, autoMotorState.motor2, autoMotorState.motor3, autoMotorState.motor4);

            //应用推力（计算机体坐标系中的推力与力矩 -> 转换到世界坐标系并积分）
            // 每个电机推力（机体坐标系 +Y 为向上）
            double t1 = this.motor1 * MAX_THRUST;
            double t2 = this.motor2 * MAX_THRUST;
            double t3 = this.motor3 * MAX_THRUST;
            double t4 = this.motor4 * MAX_THRUST;

            double totalThrust = t1 + t2 + t3 + t4;

            // 由 r x F 计算力矩（机体坐标系）
            double torqueX = -(motor1Pos.z * t1 + motor2Pos.z * t2 + motor3Pos.z * t3 + motor4Pos.z * t4);
            double torqueZ =  (motor1Pos.x * t1 + motor2Pos.x * t2 + motor3Pos.x * t3 + motor4Pos.x * t4);
            // 偏航力矩（电机自旋产生的反扭矩，电机1和3为正旋，2和4为负旋）
            double yawTorque = (t1 - t2 + t3 - t4) * K_YAW;

            Vec3 torque = new Vec3(torqueX, yawTorque, torqueZ);

            // 角加速度 alpha = torque / I
            Vec3 angAccel = new Vec3(torque.x / INERTIA, torque.y / INERTIA, torque.z / INERTIA);

            // 更新角速度并考虑角阻尼
            angularVelocity = angularVelocity.add(angAccel.scale(DT));
            angularVelocity = angularVelocity.scale(1.0 - ANGULAR_DAMPING * DT);

            // 更新姿态角（弧度）
            pitchAngle += angularVelocity.x * DT;
            yawAngle   += angularVelocity.y * DT;
            rollAngle  += angularVelocity.z * DT;

            // 将机体推力转换到世界坐标系并计算线性加速度
            Vec3 thrustBody = new Vec3(0, totalThrust, 0);
            Vec3 thrustWorld = bodyToWorld(thrustBody, pitchAngle, rollAngle, yawAngle);
            Vec3 accel = thrustWorld.scale(1.0 / MASS);

            // 更新线性速度：积分加速度并加入重力、阻尼
            velocity = velocity.add(accel.scale(DT));
            velocity = velocity.add(0, -GRAVITY * DT, 0);

            velocity = velocity.scale(1.0 - LINEAR_DRAG);

            //防止在地面上时向下的速度累计
            if (this.onGround() && velocity.y < 0) {
                velocity = new Vec3(velocity.x, 0, velocity.z);
            }

            this.setDeltaMovement(velocity);
            //this.move(MoverType.SELF, this.getDeltaMovement());

            // 更新实体显示用的角度（度）
            this.setYRot(-(float)Math.toDegrees(yawAngle));
            this.setXRot((float)Math.toDegrees(pitchAngle));
            this.entityData.set(DATA_ROLL_ANGLE, rollAngle);
            this.entityData.set(DATA_YAW_ANGLE, yawAngle);
            this.entityData.set(DATA_PITCH_ANGLE, pitchAngle);

            // 调试信息：每2tick在服务器端显示一次状态（避免客户端覆盖服务端的数据）
            debugTick++;
            if (debugTick % 2 == 0 && !this.level().isClientSide()) {
                this.setCustomName(Component.literal(String.format(
                    "vel=%.2f,%.2f,%.2f | motors=%.2f,%.2f,%.2f,%.2f",
                    velocity.x, velocity.y, velocity.z,
                    motor1, motor2, motor3, motor4
                )));
            this.setCustomNameVisible(true);
            }
        }
        
        //客户端操作：可视化电机推力
        if (this.level().isClientSide()) {
            spawnMotorParticle(motor1Pos, this.entityData.get(DATA_MOTOR1));
            spawnMotorParticle(motor2Pos, this.entityData.get(DATA_MOTOR2));
            spawnMotorParticle(motor3Pos, this.entityData.get(DATA_MOTOR3));
            spawnMotorParticle(motor4Pos, this.entityData.get(DATA_MOTOR4));
            //this.move(MoverType.SELF, this.getDeltaMovement());
        }


        this.move(MoverType.SELF, this.getDeltaMovement());
        
            
    }


    /**
     * 将机体坐标系向量转换为世界坐标系。使用的旋转顺序为：roll(绕Z) -> pitch(绕X) -> yaw(绕Y)，角度均为弧度。
     */
    private Vec3 bodyToWorld(Vec3 v, double pitch, double roll, double yaw) {
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
        double x3 = x2 * cy + z2 * sy;
        double y3 = y2;
        double z3 = -x2 * sy + z2 * cy;

        return new Vec3(x3, y3, z3);
    }

    // 客户端：在电机处沿推力方向发射粒子用于调试/可视化
    private void spawnMotorParticle(Vec3 motorBodyPos, float motorThrust) {
        if (this.level().isClientSide() && Math.abs(motorThrust) > 0.05f) {
            float pitch = this.getPitchAngle();
            float roll = this.getRollAngle();
            float yaw = this.getYawAngle();

            // 电机位置从机体坐标系转换到世界坐标系
            Vec3 offset = bodyToWorld(motorBodyPos, pitch, roll, yaw);
            double px = this.getX() + offset.x;
            double py = this.getY() + offset.y;
            double pz = this.getZ() + offset.z;

            // 推力方向（机体 +Y 方向转换到世界并取单位向量）
            Vec3 thrustDir = bodyToWorld(new Vec3(0, 1, 0), pitch, roll, yaw).normalize();

            // 我们希望粒子沿着推力的相反方向（看起来像喷射物向下喷出），速度与推力大小相关
            double speed = 0.1 + 0.4 * Math.abs(motorThrust);
            double vx = -thrustDir.x * speed;
            double vy = -thrustDir.y * speed;
            double vz = -thrustDir.z * speed;

            // 生成粒子（客户端）
            this.level().addParticle(ParticleTypes.FLAME, px, py, pz, vx, vy, vz);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    
    public void setCommand(ControlCommand command) {
        this.controlCommand = command;
    }

    public float getRollAngle() {
        return this.entityData.get(DATA_ROLL_ANGLE);
    }

    public float getPitchAngle() {
        return this.entityData.get(DATA_PITCH_ANGLE);
    }

    public float getYawAngle() {
        return this.entityData.get(DATA_YAW_ANGLE);
    }
    
}