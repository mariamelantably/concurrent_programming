/* Question 1 */

def multiplex[T](in0: ??[T], in1: ??[T], out0: !![T], out1: !![T]): ThreadGroup = thread{
    val mid = new SyncChan[(Int, T)]

    def tagger = thread{
        serve(
            in0 =?=> {x => mid!(0,x)}
            | in1 =?=> {x => mid!(1,x)}
        )
        in0.close; in1.close; mid.endOfStream
    }

    def detagger = thread{
        repeat{
        val (i, x) = mid?()
        if (i == 0) out0!x 
        else out1!x  
        }
        mid.close; out0.endOfStream; out1.endOfStream
    }
}

//The system terminates if any of the input or output ports close
//The system will only progress if the input channels are in use; if they both block, then the system will not do anything useful
//The system is fair, as the serve in the tagger allows us to alternate between both input channels.
