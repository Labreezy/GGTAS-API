package GGTAS
import com.sun.jna.Memory
import com.sun.jna.Pointer
import org.jire.kotmem.win32.Kernel32.WriteProcessMemory
import org.jire.kotmem.win32.Kernel32.ReadProcessMemory
import org.jire.kotmem.win32.Win32Process
import org.jire.kotmem.win32.openProcess
import org.jire.kotmem.win32.processIDByName
import java.nio.ByteBuffer
import kotlin.experimental.or


class TASHandler{

    var GG_PROC: Win32Process? = null
    val TOTAL_BYTES = 9616
    val REC_BASE_OFFSET : Long = 0xBAF5AC.toLong()
    fun isGearOpen(): Boolean {
        try { GG_PROC = openProcess(processIDByName("GuiltyGearXrd.exe"))
            return true
        } catch (e: IllegalStateException) {
            return false
        }
    }

    @UseExperimental(ExperimentalUnsignedTypes::class)
    private fun getPointerForSlot(slotnum: Int): Pointer? {
        if(!isGearOpen()){
            return null
        }
        if(slotnum < 1 || slotnum > 3){
            return null
        }
        val procBaseAddr: Pointer = GG_PROC!!.modules["GuiltyGearXrd.exe"]!!.pointer
        var bufferMem = Memory(4L)
        var lastPointer: Pointer = procBaseAddr
        val newPointer = Pointer(Pointer.nativeValue(lastPointer) + REC_BASE_OFFSET)
        if (ReadProcessMemory(GG_PROC!!.handle.pointer, newPointer, bufferMem, 4, 0) == 0L) {
            return null
        }
            lastPointer = Pointer(bufferMem.getInt(0L).toUInt().toLong())
        var dataAddr = Pointer(Pointer.nativeValue(lastPointer) + (slotnum - 1)*TOTAL_BYTES)
        bufferMem = Memory(TOTAL_BYTES.toLong())
        if (ReadProcessMemory(GG_PROC!!.handle.pointer, dataAddr, bufferMem, TOTAL_BYTES, 0) == 0L) {
            return null
//          throw IllegalAccessError("ReadProcMemory returned 0!")
        }
        return dataAddr
    }
    fun WriteInputsToSlot(inputs: List<Pair<String,Int>>, slotnum: Int) : Boolean{
        var slotptr : Pointer? = getPointerForSlot(slotnum)
        if(slotptr == null){
            return false
        }
        if(inputs.size > (TOTAL_BYTES - 4)/2){
            //too many inputs
            return false
        }
        var counter = 0
        for(inp in inputs){
            counter += inp.second
        }
        if(counter > (TOTAL_BYTES - 4)/2){
            //too long
            return false
        }
        var inputbuf : ByteBuffer = ByteBuffer.allocate(TOTAL_BYTES)
        inputbuf.put(0)
        inputbuf.put(0)
        inputbuf.putShort(inputs.size.toShort())
        inputbuf.put(0)
        for(input in inputs){
            var inputarr = input.first.toUpperCase().toCharArray()
            var direction = -1
            var button : Short = -1
            if(inputarr.size == 1){
                direction = 5
                button = when(inputarr[0]){
                    'P' -> InputConstants.P
                    'K' -> InputConstants.K
                    'S' -> InputConstants.S
                    'H' -> InputConstants.H
                    'D' -> InputConstants.D
                    else -> -1
                }
                if (button < 0){
                    return false
                }
                for (i in 0..input.second) {
                    inputbuf.putShort(InputConstants.DIRECTIONS[direction] or button)
                }
            }
            else if(inputarr.size == 2){
                var direction : Short = InputConstants.DIRECTIONS[inputarr[0].toInt()]
                button = when(inputarr[1]){
                    'P' -> InputConstants.P
                    'K' -> InputConstants.K
                    'S' -> InputConstants.S
                    'H' -> InputConstants.H
                    'D' -> InputConstants.D
                    else -> -1
                }
                if (button < 0){
                    return false
                }
                for(i in 0..input.second) {
                    inputbuf.putShort(direction or button)
                }
            } else {
                //input wrong size
                return false
            }
        }
        var inputbytes : ByteArray = inputbuf.array()
        var inputbytememory : Memory = Memory(inputbytes.size.toLong())
        inputbytememory.write(0L, inputbytes, 0, inputbytes.size)
        if(WriteProcessMemory(GG_PROC!!.handle.pointer, slotptr, inputbytememory, inputbytes.size, 0) == 0L){
            //write failed
            return false
        }
        return true
    }

    companion object InputConstants {
        //Numpad directions[i-1] is i in numpad, 0 is dummy value
        val DIRECTIONS : ShortArray = shortArrayOf(0b1111, 0b0110, 0b0010, 0b1010, 0b0100, 0, 0b1000, 0b0101, 0b0001, 0b1001);
        val P : Short = 0x10
        val K : Short = 0x20
        val S : Short = 0x40
        val H : Short = 0x80
        val D : Short = 0x100
    }
}