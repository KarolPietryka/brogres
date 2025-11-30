package com.dryrun.brogres.model;

import lombok.Getter;

@Getter
public enum ExcerciseEnum {
    // LEGS,
    LEG_PRESS_MCH(Area.LEGS, Equipment.MACHINE),
    RDL_BB(Area.BACK, Equipment.BARBELL),

    // ARMS,
    BICEP_CURL_DB(Area.ARMS, Equipment.DUMBBELL),
    BICEP_CURL_BB(Area.ARMS, Equipment.BARBELL),
    BICEP_CURL_MCH(Area.ARMS, Equipment.MACHINE),
    TRICEPS_MCH(Area.ARMS, Equipment.MACHINE),
    TRICEPS_DIP(Area.ARMS, Equipment.MACHINE),

    // BACK,
    DEADLIFT_BB(Area.BACK, Equipment.BARBELL),
    ROW_BB(Area.BACK, Equipment.BARBELL),
    ROW_MCH(Area.BACK, Equipment.MACHINE),
    PULL_UPS_MCH(Area.BACK, Equipment.MACHINE),

    // SHOULDERS
    OVERHEAD_BB(Area.SHOULDERS, Equipment.BARBELL),
    OVERHEAD_DB(Area.SHOULDERS, Equipment.DUMBBELL),
    OVERHEAD_MACHINE(Area.SHOULDERS, Equipment.MACHINE),
    LATERAL_RAISE_DB(Area.SHOULDERS, Equipment.DUMBBELL),
    LATERAL_RAISE_MCH(Area.SHOULDERS, Equipment.MACHINE),

    
    // ABS
    ABS_MCH(Area.ABS, Equipment.MACHINE),

    // CHEST
    CHEST_FLAT_BB(Area.CHEST, Equipment.BARBELL),
    CHEST_FLAT_MCH(Area.CHEST, Equipment.MACHINE),
    CHEST_INCLINE_MCH(Area.CHEST, Equipment.MACHINE),
    CHEST_DECLINE_MCH(Area.CHEST, Equipment.MACHINE),
    BUTTERFLY_MCH(Area.CHEST, Equipment.MACHINE);


    public enum Area {
        SHOULDERS,
        BACK,
        CHEST,
        LEGS,
        ARMS,
        ABS
    }

    public enum Equipment {
        BARBELL,
        DUMBBELL,
        MACHINE
    }

    private final Area area;
    private final Equipment equipment;

    ExcerciseEnum(Area area, Equipment equipment) {
        this.area = area;
        this.equipment = equipment;
    }

}
