import ox.scl._
class SortNet{

    //Question 1
    def comparator(in0: InPort[Int], in1: InPort[Int], out0: OutPort[Int], out1: OutPort[Int]) : ThreadGroup = thread("comparator"){
        repeat{
            var x, y = 0
            run(thread{x = in0?()}||thread{y = in1?()})
            run(thread{out0!(x min y)} || thread{out1!(y max x)})
        } 
        in1.close; in0.close
        out0.endOfStream;out1.endOfStream
    }


    //Question 2
    def sort4(ins: List[InPort[Int]], outs: List[OutPort[Int]]) = thread("sort4"){
        val ints = List(new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int])
        require(ins.length == 4 && outs.length == 4)
        val comparator1 = comparator(ins(0), ins(2), ints(0), ints(2))
        val comparator2 = comparator(ins(1), ins(3), ints(1), ints(3))
        val comparator3 = comparator(ints(0), ints(1), outs(0) , ints(4))
        val comparator4 = comparator(ints(2), ints(3), ints(5), outs(3))
        val comparator5 = comparator(ints(4), ints(5), outs(1), outs(2))
        run(comparator1 || comparator2 || comparator3 || comparator4 || comparator5)
    }

    //Question 3
    def insert(ins: List[??[Int]], in: ??[Int], outs: List[!![Int]]) : ThreadGroup = thread{
        val n = ins.length; require(n >= 1 && outs.length == n + 1)
        if (n == 1){
            run(comparator(ins(0), in, outs(0), outs(1)))
        }
        else{
            var intermediate =  new SyncChan[Int]
            run(comparator(ins(0), in, outs(0), intermediate) || insert(ins.tail, intermediate, outs.tail))
        }
    }

    //Question 4
    def bin_search_insert(ins: List[??[Int]], in: ??[Int], outs: List[!![Int]]) : ThreadGroup = thread{


        val n = ins.length; require(n >= 1 && outs.length == n + 1)
        if (n == 1){
            run(comparator(ins(0), in, outs(0), outs(1)))
        }
        else{
            if (n == 2){
                val a = new SyncChan[Int]
                run(comparator(ins(0), in, outs(0), a) || comparator(a, ins(1), outs(1), outs(2)))
            }
            else{
                val a, b = new SyncChan[Int]
                val m = n/2
                val (out1, out2) = outs.splitAt(m + 1)
                val (in1, in2) = ins.splitAt(m)
                run(comparator(in, ins(m), a, b) || bin_search_insert(in1, a, out1) || bin_search_insert(in2.tail, b, out2))
            }
        }

    }

    def insertionSort(ins: List[??[Int]], outs: List[!![Int]]) : ThreadGroup = thread{
        val n = ins.length; require(n >= 2 && outs.length == n)
        if (n == 2){
            run(bin_search_insert(ins.tail, ins(0), outs))
        }
        else{
            var intermediates : List[SyncChan[Int]] = List()
            for (i<- 0 until n-1) intermediates = new SyncChan[Int]::intermediates
            run(insertionSort(ins.tail, intermediates) || bin_search_insert(intermediates, ins(0), outs))
        }
    }
}

object SortNet{
    val a = Array(1, 2, 3, 5, 6, 7, 8)
    val ins  = List(new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int])
    val outs = List(new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int], new SyncChan[Int],new SyncChan[Int],  new SyncChan[Int])
    val in = new SyncChan[Int]
    def send(i : Int) = thread{ins(i)!a(i); ins(i).close}

    def receive = thread{
        for (i <- 0 until 8) {val c = outs(i)?(); print(c); print(" "); outs(i).endOfStream;}
    }

    def send1 = thread{
        {in!4; in.close}
    }

    def main(args : Array[String]) = {
        val p = new SortNet()
        var t = send(0)
        for (i <- 1 until 7){t = t || send(i)}
        run(t || p.bin_search_insert(ins, in, outs) || send1 || receive)
    }
}