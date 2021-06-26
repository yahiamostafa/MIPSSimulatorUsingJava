import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
public class Main {
	public static int []memory;
	public static int [] regs;
	public static int PC;
	public static Queue<int []> decQueue,exQueue,memQueue,wbQueue;
	public final static int R0 = 0;
	public static HashMap<String, Integer> opcodes;
	public static FileWriter output;
	public static int last_fetch_cycle =0,last_decode_cycle=0;
	public static int total_number_of_instructions ,pc_stall =-100,pc_stall2=-100;
	public static int [] decodeInput = null,executeInput = null,memoryInput = null,write_backInput = null;
public static void main(String[] args) throws IOException {
	init_Opcodes();
	memory = new int[2048];
	regs = new int [33];
	decQueue = new LinkedList<int[]>();
	exQueue = new LinkedList<int[]>();
	memQueue = new LinkedList<int[]>();
	wbQueue = new LinkedList<int[]>();
//	regs[2] =16;
	regs[3] = 15;
	output = new FileWriter("src\\output.txt");
	BufferedReader bufferedReader = new BufferedReader(new FileReader("src\\Assembly.txt"));
	String current ;
	ArrayList<String> temp = new ArrayList<String>();
	while ((current=bufferedReader.readLine())!=null) {
		if (!current.contains("#") && !current.isEmpty()) // # if the line is a comment.
			temp.add(current);
	}
	total_number_of_instructions = temp.size();
	parse_instructions(temp);
	if (total_number_of_instructions !=0)
		run();
	System.out.println(memory[1030]);
	printings();
	output.close();
	bufferedReader.close();
}
public static int return_type(int x) {
	if (x==0||x==1||x==9||x==8)
		return 0; // R-type
	if (x==7)
		return 1; //J-type
	
	return 2; //I-Type 
}
public static void parse_instructions(ArrayList<String> tempArray) {
	int i= 0;
	for (String a : tempArray) {
		a = a.replace("("," ");
		if (i>=1024) {
			System.err.println("There is no more space for instructions you should\n"
					+ "optimize your code the maximum number of instructions allowed\n"
					+ "is 1024");
		}
		int instruction_address = 0;
		// in case there are no labels in the line .
		if (!a.contains(":")) {
			String [] instruction = a.split(" ");
			int index = opcodes.getOrDefault(instruction[0].toLowerCase(), -1);
			if (index ==-1) {
				System.err.println("Unrecodnized instrution !!!");
				return;
			}
			int opcode = index;
			int type = return_type(opcode);
			 instruction_address= opcode<<28;
			if (type==1) {
				try {
					int jump_address = Integer.parseInt(instruction[1]);
					instruction_address+=jump_address;
				}catch (Exception e) {
					System.err.println("You should pass an immediate value");
					return;
				}
			}else {
				int destReg;
				try {
					destReg =  Integer.parseInt(instruction[1].replaceAll("[$Rr() ]", ""));
//					System.out.println(destReg);
//					System.out.println(Integer.toBinaryString(destReg<<23+instruction_address));
				}catch (Exception e) {
					System.err.println("This Register "+instruction[1]+" doesn't exist");
					return;
				}
				destReg<<=23;
				instruction_address+= destReg;
//				System.out.println(Integer.toBinaryString(instruction_address));
				int srcReg1;
				try {
					srcReg1 =  Integer.parseInt(instruction[2].replaceAll("[$Rr() ]", ""));
				}catch (Exception e) {
					System.err.println("This Register "+instruction[2]+" doesn't exist");
					return;
				}
				srcReg1<<=18;
				instruction_address+= srcReg1;
//				System.out.println(Integer.toBinaryString(instruction_address));
				if (type == 0) {
				if (opcode==9 || opcode==8) {
					int shamt ;
					try {
						shamt = Integer.parseInt(instruction[3].replaceAll("[$Rr() ]", ""));
						instruction_address+=shamt;
					}catch (Exception e) {
						System.err.println("This Register "+instruction[3]+" doesn't exist");
						return;
					}
					}else {
						int srcReg2;
						try {
							srcReg2 = Integer.parseInt(instruction[3].replaceAll("[$Rr() ]", ""));
						}catch (Exception e) {
							System.err.println("This Register "+instruction[3]+" doesn't exist");
							return;
						}
						srcReg2<<=13;
						instruction_address+= (srcReg2);
					}
			}else {
				try {
				int imm = Integer.parseInt(instruction[3].replaceAll("[$Rr() ]", ""));
				imm &=0b111111111111111111;
				instruction_address+=imm;
				}catch (Exception e) {
					System.err.println("You should pass an immediate value");
					return;
				}
			}
		}
		}
//		System.out.println(Integer.toBinaryString(instruction_address));
		memory[i] = instruction_address;
		System.out.println(Integer.toBinaryString(instruction_address));
		i++;
	}
}
public static void init_Opcodes() {
	opcodes = new HashMap<String, Integer>();
	opcodes.put("add",0);
	opcodes.put("sub",1);
	opcodes.put("muli",2);
	opcodes.put("addi",3);
	opcodes.put("bne",4);
	opcodes.put("andi",5);
	opcodes.put("ori",6);
	opcodes.put("j",7);
	opcodes.put("sll",8);
	opcodes.put("srl",9);
	opcodes.put("lw",10);
	opcodes.put("sw",11);
}

public static void run() throws IOException {
 	for (int cycle = 1;1!=2;cycle++) {
 		if (PC >=total_number_of_instructions && decQueue.isEmpty() && exQueue.isEmpty() && memQueue.isEmpty() && wbQueue.isEmpty())
 			return;
 		System.out.println("At clock cycle "+cycle+" :");
 		output.write("At clock cycle "+cycle+" :\n");
		if (cycle % 2 ==1) {
			if (PC  < total_number_of_instructions) {
				System.out.println("Input to fetch (PC : "+PC+" )");
				output.write("Input to fetch (PC"+PC+" )\n");
				last_fetch_cycle = cycle;
				decQueue.add(fetch(PC));
			}
			if (!decQueue.isEmpty() && cycle>=3) {
				decodeInput = decQueue.peek();
				if (!(decodeInput[1]+1 == PC && last_fetch_cycle == cycle)) {
				System.out.println("Input to decode (PC: " +decodeInput[1]+", instruction "+Integer.toBinaryString(decodeInput[0]) +" )");
				output.write("Input to decode (PC: " +decodeInput[1]+", instruction "+Integer.toBinaryString(decodeInput[0]) +" )\n");
				decQueue.poll();
				last_decode_cycle = cycle;
				exQueue.add(decode(decodeInput,true));
				}
			}
			if (!exQueue.isEmpty() && cycle>=5) {
				executeInput = exQueue.peek();
				if (!(executeInput[8]==decodeInput[1] && last_decode_cycle ==cycle)) {
				System.out.println("Input to Execute ( opcode: "+executeInput[0]+", rd: "+executeInput[1]+", rs: "+executeInput[2]+", rt: "+executeInput[3]+", immediate value: "+executeInput[4]+", address: "+executeInput[5]+", shamt: "+executeInput[6]+", rd_index: "+executeInput[7]+")");
				output.write("Input to Execute ( opcode: "+executeInput[0]+", rd: "+executeInput[1]+", rs: "+executeInput[2]+", rt: "+executeInput[3]+", immediate value: "+executeInput[4]+", address: "+executeInput[5]+", shamt: "+executeInput[6]+", rd_index: "+executeInput[7]+")\n");
				exQueue.poll();
				memQueue.add(execute(executeInput[0], executeInput[1], executeInput[2], executeInput[3], executeInput[4], executeInput[5], executeInput[6],executeInput[7],executeInput[8],executeInput[9],true));
				}
				}
			if (!wbQueue.isEmpty() && cycle>=7) {
				write_backInput = wbQueue.poll();
				System.out.println("Write back input ( destRegIndex: "+write_backInput[0]+", Result :"+write_backInput[1]+", Is writing back :"+(write_backInput[2]==1)+", Will flush :"+(write_backInput[4]==1)+" )");
				output.write("Write back input ( destRegIndex: "+write_backInput[0]+", Result :"+write_backInput[1]+", Is writing back :"+(write_backInput[2]==1)+", Will flush :"+(write_backInput[4]==1)+" )\n");
				writeback(write_backInput[0],write_backInput[1], write_backInput[2]==1?true:false , write_backInput[3],write_backInput[4]);
			}
		}else {
			if (!decQueue.isEmpty()) {
				decodeInput = decQueue.peek();
				System.out.println("Input to decode (PC: " +decodeInput[1]+", instruction "+Integer.toBinaryString(decodeInput[0]) +" )");
				output.write("Input to decode (PC: " +decodeInput[1]+", instruction "+Integer.toBinaryString(decodeInput[0]) +" )\n");
				decode(decodeInput,false);
			}
			if (!exQueue.isEmpty() && cycle>=4) {
				executeInput = exQueue.peek();
				System.out.println("Input to Execute ( opcode: "+executeInput[0]+", rd: "+executeInput[1]+", rs: "+executeInput[2]+", rt: "+executeInput[3]+", immediate value: "+executeInput[4]+", address: "+executeInput[5]+", shamt: "+executeInput[6]+", rd_index: "+executeInput[7]+")");
				output.write("Input to Execute ( opcode: "+executeInput[0]+", rd: "+executeInput[1]+", rs: "+executeInput[2]+", rt: "+executeInput[3]+", immediate value: "+executeInput[4]+", address: "+executeInput[5]+", shamt: "+executeInput[6]+", rd_index: "+executeInput[7]+")\n");
				execute(executeInput[0], executeInput[1], executeInput[2], executeInput[3], executeInput[4], executeInput[5], executeInput[6],executeInput[7],executeInput[8],executeInput[9],false);
			}
			if (!memQueue.isEmpty() && cycle>=6) {
				memoryInput = memQueue.poll();
				System.out.println("Input to Memory (Address : "+memoryInput[0]+", destreg_index: "+memoryInput[1]+", Will use the memory :"+(memoryInput[2]==1)+", Value will be stored in the memory: "+memoryInput[3]+", Will Write back : "+(memoryInput[4]==1)+", Will flush : "+(memoryInput[6]==1)+ " )");
				output.write("Input to Memory (Address : "+memoryInput[0]+", destreg_index: "+memoryInput[1]+", Will use the memory :"+(memoryInput[2]==1)+", Value will be stored in the memory: "+memoryInput[3]+", Will Write back : "+(memoryInput[4]==1)+", Will flush : "+(memoryInput[6]==1)+ " )\n");
				wbQueue.add(memory(memoryInput[0],memoryInput[1],memoryInput[2]==1?true:false,memoryInput[3],memoryInput[4]==1?true:false,memoryInput[5],memoryInput[6]));
			}
			
		}
		System.out.println("_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-");
		output.write("_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-\n");
	}
}
private static int [] fetch(int pc) throws IOException {
	System.out.println("Fetching instruction "+(pc+1));
	output.write("Fetching instruction "+(pc+1)+'\n');
	PC++;
	return new  int []{memory[pc],pc};
}
private static int [] decode(int [] inst , boolean odd) throws IOException {
	if (odd && pc_stall2 == inst[1]) {
		pc_stall2 = -100;
		System.out.println("Flushing Decoding instruction "+(inst[1]+1));
		output.write("Flushing Decoding instruction "+(inst[1]+1)+'\n');
		return new int [] {0,0,0,0,0,0,0,0,inst[1],1};
	}
	if (!odd && pc_stall2 == inst[1]) {
		System.out.println("Flushing Decoding instruction "+(inst[1]+1));
		output.write("Flushing Decoding instruction "+(inst[1]+1)+'\n');
		return new int [] {0,0,0,0,0,0,0,0,inst[1],1}; 
	}
	System.out.println("Decoding instruction "+(inst[1]+1));
	output.write("Decoding instruction "+(inst[1]+1)+'\n');
	if (!odd) {
		return executeInput;
	}
//	System.out.println("Decoding instruction "+(inst[1]+1));
	int opcode,rd,rs,rt,address,shamt,imm,rd_index;
	opcode = inst[0]&0b11110000000000000000000000000000;
	opcode>>>=28;
	rd = inst[0]&0b00001111100000000000000000000000;
	rd>>>=23;
	rd_index = rd;
	rd = regs[rd];
	rs = inst[0]&0b00000000011111000000000000000000;
	rs>>>=18;
	rs = regs[rs];
	rt = inst[0]&0b00000000000000111110000000000000;
	rt>>>=13;
	rt =regs[rt];
	shamt = inst[0]&0b1111111111111;
	address = inst[0]&0b1111111111111111111111111111;
	imm = inst[0]&0b111111111111111111;
	int sign_bit = (imm&0b100000000000000000)>>17;
	if (sign_bit==1) {
		String temp = Integer.toBinaryString(imm);
		int sum =0;
		for (int i=1;i<18;i++) {
			if (temp.charAt(i)=='1') {
				sum+=Math.pow(2, 17-i);
			}
		}
		sum-=Math.pow(2, 17);
		imm =sum;
	}
	return new int  [] {opcode, rd, rs, rt, imm, address,shamt,rd_index,inst[1],0};
}
private static  int [] execute(int opcode,int rd,int rs,int rt,int imm,int address,int shamt,int rd_index,int pc,int flush,boolean odd) throws IOException {
	if (odd && pc_stall == pc-1 || flush ==1) {
		pc_stall = -100;
		System.out.println("Flushing Executing instruction "+(pc+1));
		output.write("Flushing Executing instruction "+(pc+1)+'\n');
		return new int [] {0,0,0,0,0,pc,1}; 
	}
	if (!odd && pc_stall == pc-1 || flush==1) {
		System.out.println("Flushing Executing instruction "+(pc+1));
		output.write("Flushing Executing instruction "+(pc+1)+'\n');
		return new int [] {0,0,0,0,0,pc,1}; 
	}
	System.out.println("Executing instruction "+(pc+1));
	output.write("Executing instruction "+(pc+1)+'\n');
	if (!odd) 
		return memoryInput;
	int result = -1;
	switch(opcode) {
	case(0):result = rs + rt;return new int [] {result,rd_index,0,-1,1,pc,0};
	case(1):result = rs - rt;return new int [] {result,rd_index,0,-1,1,pc,0};
	case(2):result = rs * imm;return new int [] {result,rd_index,0,-1,1,pc,0};
	case(3):result = rs + imm;return new int [] {result,rd_index,0,-1,1,pc,0};
	case(4):result = (rd-rs);
	if(result!=0) {
		pc_stall = pc;
		PC+=imm-2;
		pc_stall2 = pc+2;
	}
	return new int [] {result,rd,0,-1,0,pc,0};
	case(5):result = rs & imm;return new int [] {result,rd_index,0,-1,1,pc,0};
	case(6):result = rs | imm;return new int [] {result,rd_index,0,-1,1,pc,0};
	case(7):
	PC = address;
	pc_stall = pc;
	pc_stall2 = pc+2;
	return new int [] {result,rd,0,-1,0,pc,0};
	case(8):result = rs << shamt;return new int [] {result,rd_index,0,-1,1,pc,0};
	case(9):result = rs >>> shamt;return new int [] {result,rd_index,0,-1,1,pc,0};
	case(10):result = rs + imm;return new int [] {result,rd_index,1,-1,1,pc,0};
	// TODO 
	case(11):result = rs + imm;return new int [] {result,rd,1,1,0,pc,0};
	}
	return null;
}

private static  int [] memory(int address,int rd_index,boolean used,int  load ,boolean will_write_back, int pc , int flush ) throws IOException {
	if (flush ==1) {
		System.out.println("flushing Memory instruction "+(pc+1));
		output.write("flushing Memory instruction "+(pc+1)+'\n');
		return new int [] {0,0,0,pc,1};
	}
	System.out.println("Memory instruction "+(pc+1));
	output.write("Memory instruction "+(pc+1)+'\n');
	if (used) {
	if (!(address>=1024 && address<=2047))
		System.err.println("Address is out of bound your address should have a range from 1024 up to 2047 but your address value is "+address);
	else if(load == -1) {
		return new int [] {rd_index, memory[address],1,pc,0}; 
	}else {
		memory[address] = rd_index;
		System.out.println("Value in memory location "+address+" is changed to "+rd_index);
		output.write("Value in memory location "+address+" is changed to "+rd_index+'\n');
		return new int [] {rd_index , -1 , 0 ,pc,0};
	}
	}
	return new int [] {rd_index,address , will_write_back?1:0,pc,0};
}

private static  void writeback(int reg,int result , boolean write_back, int pc ,int flush) throws IOException {
	if (flush ==1) {
		System.out.println("Flushing Writing Back instruction "+(pc+1));
		output.write("Flushing Writing Back instruction "+(pc+1)+'\n');
		return;
	}
	System.out.println("Writing Back instruction "+(pc+1));
	output.write("Writing Back instruction "+(pc+1)+'\n');
	if (write_back)
		if (reg!=0) {
			regs[reg] = result;
			System.out.println("Value inside register R"+reg+" changed to "+result);
			output.write("Value inside register R"+reg+" changed to "+result+'\n');
		}
}

public static void printings() throws IOException {
	StringBuilder sb = new StringBuilder();
	int c =0;
	sb.append("Register Name ---------------  HexValue -----------DecimalValue\n");
	output.write("Register Name ---------------  HexValue -----------DecimalValue\n");
	sb.append("PC -------------------------- "+Integer.toHexString(PC)+" ------------------ "+PC+'\n');
	output.write("PC -------------------------- "+Integer.toHexString(PC)+" ------------------ "+PC+'\n');
	for (int regValue : regs) 
		sb.append("Register R"+c++ +" ----------------- "+ Integer.toHexString(regValue)+"---------------------- "+regValue+"\n");
	c =0;
	sb.append("Memory Address ---------------  HexValue -----------DecimalValue ------------- BinaryValue\n");
	for (int memAdd : memory)
		sb.append("Mem["+c++ +"]" +" ----------------- "+ Integer.toHexString(memAdd)+"---------------------- "+memAdd+" ---------------------------"+Integer.toBinaryString(memAdd)+"\n");
	System.out.println(sb);
	output.write(sb.toString());
}
}
