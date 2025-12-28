package com.example.examplemod.autopilot;

public class ControlCommand {
    //玩家输入的控制命令，作为一个数据集合来传输

    //输入量
    public float referenceThrottle; //0.0 ~ 1.0
    public float referenceYawSpeed; //-1.0 ~ 1.0
    public float referencePitch;    //-1.0 ~ 1.0 
    public float referenceRoll;     //-1.0 ~ 1.0 
    

    public ControlCommand() {
        // 初始化控制命令
    }
}
