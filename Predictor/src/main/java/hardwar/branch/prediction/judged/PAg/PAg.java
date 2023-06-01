package hardwar.branch.prediction.judged.PAg;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAg implements BranchPredictor {
    private final ShiftRegister SC; // saturating counter register
    private final RegisterBank PABHR; // per address branch history register
    private final Cache<Bit[], Bit[]> PHT; // page history table
    public PAg() {
        this(4, 2, 8);
    }

    /**
     * Creates a new PAg predictor with the given BHR register size and initializes the PABHR based on
     * the branch instruction size and BHR size
     *
     * @param BHRSize               the size of the BHR register
     * @param SCSize                the size of the register which hold the saturating counter value
     * @param branchInstructionSize the number of bits which is used for saving a branch instruction
     */
    public PAg(int BHRSize, int SCSize, int branchInstructionSize) {
        // TODO: complete the constructor
        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(1<<branchInstructionSize, BHRSize);

        // Initialize the PHT with a size of 2^size and each entry having a saturating counter of size "SCSize"
        PHT = new PageHistoryTable(1<<BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SC", SCSize, null);
    }

    /**
     * @param instruction the branch instruction
     * @return the predicted outcome of the branch instruction (taken or not taken)
     */
    @Override
    public BranchResult predict(BranchInstruction instruction) {
        ShiftRegister shiftRegister = PABHR.read(instruction.getInstructionAddress());
        PHT.putIfAbsent(shiftRegister.read(), getDefaultBlock());
        Bit[] bits = PHT.get(shiftRegister.read());
        if(bits[0].getValue()){
            return BranchResult.TAKEN;
        }
        return BranchResult.NOT_TAKEN;
    }

    /**
     * @param instruction the branch instruction
     * @param actual      the actual result of branch (taken or not)
     */
    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        // TODO: complete Task 2
        ShiftRegister shiftRegister = PABHR.read(instruction.getInstructionAddress());
        PHT.putIfAbsent(shiftRegister.read(), getDefaultBlock());
        Bit[] bits = PHT.get(shiftRegister.read());
        Bit[] result;
        if(BranchResult.isTaken(actual)){
            result = CombinationalLogic.count(bits, true, CountMode.SATURATING);
        }else{
            result = CombinationalLogic.count(bits, false, CountMode.SATURATING);
        }
        PHT.put(shiftRegister.read(), result);
        if(BranchResult.isTaken(actual))
        shiftRegister.insert(Bit.ONE);
        else
        shiftRegister.insert(Bit.ZERO);
        PABHR.write(instruction.getInstructionAddress(), result);
    }

    /**
     * @return a zero series of bits as default value of cache block
     */
    private Bit[] getDefaultBlock() {
        Bit[] defaultBlock = new Bit[SC.getLength()];
        Arrays.fill(defaultBlock, Bit.ZERO);
        return defaultBlock;
    }

    @Override
    public String monitor() {
        return "PAg predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PHT.monitor();
    }
}
