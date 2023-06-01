package hardwar.branch.prediction.judged.PAp;

import hardwar.branch.prediction.shared.*;
import hardwar.branch.prediction.shared.devices.*;

import java.util.Arrays;

public class PAp implements BranchPredictor {

    private final int branchInstructionSize;

    private final ShiftRegister SC; // saturating counter register

    private final RegisterBank PABHR; // per address branch history register

    private final Cache<Bit[], Bit[]> PAPHT; // Per Address Predication History Table

    public PAp() {
        this(4, 2, 8);
    }

    public PAp(int BHRSize, int SCSize, int branchInstructionSize) {
        this.branchInstructionSize = branchInstructionSize;

        // Initialize the PABHR with the given bhr and branch instruction size
        PABHR = new RegisterBank(branchInstructionSize, BHRSize);

        // Initializing the PAPHT with BranchInstructionSize as PHT Selector and
        // 2^BHRSize row as each PHT entries
        // number and SCSize as block size
        PAPHT = new PerAddressPredictionHistoryTable(branchInstructionSize, 1 << BHRSize, SCSize);

        // Initialize the SC register
        SC = new SIPORegister("SC", SCSize, null);
    }

    @Override
    public BranchResult predict(BranchInstruction branchInstruction) {
        ShiftRegister shiftRegister = PABHR.read(branchInstruction.getInstructionAddress());
        this.PAPHT.putIfAbsent(getCacheEntry(branchInstruction.getInstructionAddress(), shiftRegister.read()),
                getDefaultBlock());
        this.SC.load(this.PAPHT.get(getCacheEntry(branchInstruction.getInstructionAddress(), shiftRegister.read())));
        return BranchResult.of(this.SC.read()[0].getValue());
    }

    @Override
    public void update(BranchInstruction instruction, BranchResult actual) {
        ShiftRegister shiftRegister = PABHR.read(instruction.getInstructionAddress());
        this.PAPHT.put(getCacheEntry(instruction.getInstructionAddress(), shiftRegister.read()),
                CombinationalLogic.count(this.SC.read(), BranchResult.isTaken(actual), CountMode.SATURATING));
        if (BranchResult.isTaken(actual))
            shiftRegister.insert(Bit.ONE);
        else
            shiftRegister.insert(Bit.ZERO);
        PABHR.write(instruction.getInstructionAddress(), shiftRegister.read());
    }

    private Bit[] getCacheEntry(Bit[] branchAddress, Bit[] BHRValue) {
        // Concatenate the branch address bits with the BHR bits
        Bit[] cacheEntry = new Bit[branchAddress.length + BHRValue.length];
        System.arraycopy(branchAddress, 0, cacheEntry, 0, branchInstructionSize);
        System.arraycopy(BHRValue, 0, cacheEntry, branchAddress.length, BHRValue.length);
        return cacheEntry;
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
        return "PAp predictor snapshot: \n" + PABHR.monitor() + SC.monitor() + PAPHT.monitor();
    }
}
