import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObjectCodeManager {
    int global_offset = 0;
    int t_num = 0;//下一个分配t t_num号寄存器 共10个
    private String alloc_treg(){
        String ans ="$t" + t_num %10 ;
        t_num++;
        return ans;
    }
    int s_num = 0;//下一个分配s s_num号寄存器 共8个
    private String alloc_sreg(){
        if(s_num < 8){
            String ans = "$s" + s_num;
            s_num++;
            return ans;
        }
        return "s_reg_overflow!!";
    }
    public void output_to_file() throws IOException {
        File file = new File("mips.txt");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(this.output_data_and_text());
        writer.close();
    }

    List<MidCode> midCodes = new ArrayList<>();
    StringBuilder mipses = new StringBuilder();
    StringBuilder global_decl = new StringBuilder();
    List<DataEntry> datas = new ArrayList<>();
    List<FuncDefEntry> funcDefs = new ArrayList<>();
    Map<String,String> xt2$tx = new HashMap<>();
    Map<String,String> operator2operation = new HashMap<>();
    Map<String,String> symbol2instr = new HashMap<>();

    Map<String,Symbol_Table> name2symboltable = new HashMap<>();

    int arr = 0;//为0表示正在处理一维数组，为1表示正在处理二维数组的第一维，为2表示正在处理二维数组的第二维
    public String output_data_and_text(){
        StringBuilder ans = new StringBuilder();
        ans.append(".data\n");

        for(DataEntry d : this.datas){
            ans.append(d.str_ident + ": .asciiz " + d.str_content + "\n");
        }

        ans.append(".text\n");
        //全局声明部分
        ans.append(this.global_decl);
        ans.append("jal main\n");
        ans.append("nop\n");
        ans.append("li $v0 10\nsyscall\n");

        //函数定义部分
        for(FuncDefEntry funcDefEntry:this.funcDefs){
            ans.append(funcDefEntry.funcName).append(":\n");
            ans.append(funcDefEntry.funcMipses);
        }

        //main函数内部
        ans.append(this.mipses);
        return ans.toString();
    }
    private void init(){
        operator2operation.put("+","add");
        operator2operation.put("-","sub");
        operator2operation.put("*","mul");
        operator2operation.put("/","div");
        operator2operation.put("%","rem");

        symbol2instr.put("<","blt");
        symbol2instr.put("<=","ble");
        symbol2instr.put(">","bgt");
        symbol2instr.put(">=","bge");
        symbol2instr.put("===","beq");
        symbol2instr.put("!=","bne");


    }
    public ObjectCodeManager(List<MidCode> midCodes,Map<String,Symbol_Table> name2symboltable){
        init();
        this.midCodes = midCodes;
        this.name2symboltable = name2symboltable;
        generateObjectCode();
    }
    public void generateObjectCode() {
        StringBuilder curMipses = new StringBuilder();
        curMipses  = global_decl;
        for (MidCode m : this.midCodes) {
            if (m.operation.equals("const int")) {//常量声明，只需填表
                if (m.res == null || (m.res.charAt(m.res.length() - 1) != 't'||Character.isDigit(m.res.charAt(0)))) {//全局常量和能算出结果的表达式一定会进入这个分支
                    m.operand1.value = m.result;//符号表存值，不需要mips指令
                } else {//表达式赋值
                    String location = m.res;
                    if (location.charAt(location.length() - 1) == 't'&&Character.isDigit(location.charAt(0))) {//必进
                        if(m.symbol_table.lookupSymbol(location,m.use_line)==null){
                            location = "$t" + ((t_num + 9) % 10);//找到表达式的存储位置(等价于（t-1）%10)
                        }else{
                            Symbol symbol = m.symbol_table.lookupSymbol(location,m.use_line);
                            if(symbol.reg!=null){
                                location = symbol.reg;
                            }else if(symbol.offset!=-1){
                                String temp = alloc_treg();
                                String lw = "lw " +temp + " " + symbol.offset*4 + "($gp)\n";
                                curMipses.append(lw);
                                location = temp;
                            }
                        }
                    }
                    //为常量分配存储空间
                    String target = "";
                    if (s_num >= 8|| m.symbol_table.funcName.equals("out")) {//s已满或全局常量
                        int offset = global_offset*4;
                        String move_gp = "sw " + location + " " + offset + "($gp)\n";
                        curMipses.append(move_gp);
                        //改符号表，记录地址
                        m.operand1.offset = global_offset;
                        global_offset++;
                    } else {//存到寄存器里
                        target = "$s" + s_num;
                        s_num++;
                        String store_to_reg = "move " + target + " " + location + "\n";
                        curMipses.append(store_to_reg);
                        //改符号表，记录寄存器地址
                        m.operand1.reg = target;
                    }
                }
            }else if(m.operation.equals("const int array")){//声明常量数组，必有初始化
                //分配地址，改符号表
                int offset = global_offset *4;//即将分得的偏移
                Symbol array = m.symbol_table.lookupSymbol(m.operand1.name,m.operand1.decl_line);
                array.offset = global_offset;//记录数组基地址
                //分配内存，移动指针
                if(array.dimension == 1){
                    global_offset += array.fir_dim;
                }else if(array.dimension == 2){
                    global_offset += array.fir_dim*array.sec_dim;
                }
            }else if(m.operation.equals("var int array")){//声明变量数组，不一定有初始化
                //分配地址，改符号表
                int offset = global_offset *4;//即将分得的偏移
                Symbol array = m.symbol_table.lookupSymbol(m.operand1.name,m.operand1.decl_line);
                array.offset = global_offset;//记录数组基地址
                //分配内存，移动指针
                if(array.dimension == 1){
                    global_offset += array.fir_dim;
                }else if(array.dimension == 2){
                    global_offset += array.fir_dim*array.sec_dim;
                }


            }else if (m.operation.equals("var int")) {//变量声明，分有无初始化两种情况,未初始化无需输出mips代码
                if (m.res != null) {//有初始化，需要改表
                    boolean knownValue = false;
                    if ((m.res.charAt(m.res.length() - 1) != 't'||!Character.isDigit(m.res.charAt(0)))&&m.res.charAt(0)!='$') {
                        if(m.symbol_table.lookupSymbol(m.res,m.use_line)!=null){//变量
                            Symbol r = m.symbol_table.lookupSymbol(m.res,m.use_line);
                            if(r.reg==null&&r.offset==-1){//直接改符号表即可
                                m.operand1.value = r.value;
                                knownValue = true;
                            }else if(r.offset != -1){//内存取值
                                String lw = "lw " + alloc_treg() + " " +r.offset*4 + "($gp)\n";
                                curMipses.append(lw);
                            }else if(r.reg != null){//寄存器取值
                                String move_reg = "move " + alloc_treg() + " " + r.reg + "\n";
                                curMipses.append(move_reg);
                            }

                            //为变量分配存储空间

                                String target = "";
                                if (s_num >= 8 || m.symbol_table.funcName.equals("out")) {//s已满或全局变量
                                    if(knownValue){
                                        String li = "li " + alloc_treg() + " " + m.operand1.value + "\n";
                                        curMipses.append(li);
                                    }
                                    int offset = global_offset*4;
                                    String move_gp = "sw $t" + (t_num + 9)%10 + " " + offset + "($gp)\n";
                                    curMipses.append(move_gp);
                                    //改符号表，记录地址
                                    m.operand1.offset = global_offset;
                                    global_offset++;
                                } else {//存到寄存器里
                                    if(knownValue){
                                        String li = "li " + alloc_treg() + " " + m.operand1.value + "\n";
                                        curMipses.append(li);
                                    }
                                    target = "$s" + s_num;
                                    s_num++;
                                    String store_to_reg = "move " + target + " $t" + (t_num +9)%10 + "\n";
                                    curMipses.append(store_to_reg);
                                    //改符号表，记录寄存器地址
                                    m.operand1.reg = target;
                                }



                        }else{//数值
                            m.operand1.value = m.result;//符号表存值，需要mips指令,也给变量分配存储空间
                            String li = "li " + alloc_treg() + " " + m.result + "\n";
                            curMipses.append(li);
                            String target = "";
                            if (s_num >= 8 || m.symbol_table.funcName.equals("out")) {//s已满或全局变量
                                int offset = global_offset*4;
                                String move_gp = "sw $t" + (t_num + 9)%10 + " " + offset + "($gp)\n";
                                curMipses.append(move_gp);
                                //改符号表，记录地址
                                m.operand1.offset = global_offset;
                                global_offset++;
                            } else {//存到寄存器里
                                target = "$s" + s_num;
                                s_num++;
                                String store_to_reg = "move " + target + " $t" + (t_num +9)%10 + "\n";
                                curMipses.append(store_to_reg);
                                //改符号表，记录寄存器地址
                                m.operand1.reg = target;
                            }
                        }
                    }else{//表达式赋值
                        String location = m.res;
                        if (location.charAt(location.length() - 1) == 't'&&Character.isDigit(location.charAt(0))||location.charAt(0)=='$') {//必进
                            location = "$t" + ((t_num + 9) % 10);//找到表达式的存储位置(等价于（t-1）%10)
                        }else{
                            Symbol s = m.symbol_table.lookupSymbol(location,m.use_line);
                            if(s!=null){
                                if(s.reg!=null){
                                    location = s.reg;
                                }else if(s.offset != -1){
                                    location = alloc_treg();
                                    String lw = "lw " + location + " " + s.offset*4 + "($gp)\n";
                                    curMipses.append(lw);
                                }else{
                                    location = alloc_treg();
                                    String li = "li " + location + " " + s.offset*4 + "($gp)\n";
                                    curMipses.append(li);
                                }
                            }
                        }
                        //为变量分配存储空间
                        String target = "";
                        if (s_num >= 8 || m.symbol_table.funcName.equals("out")) {//s已满或全局变量
                            int offset = global_offset*4;
                            String move_gp = "sw " + location + " " + offset + "($gp)\n";
                            curMipses.append(move_gp);
                            //改符号表，记录地址
                            m.operand1.offset = global_offset;
                            global_offset++;
                        } else {//存到寄存器里
                            target = "$s" + s_num;
                            s_num++;
                            String store_to_reg = "move " + target + " " + location + "\n";
                            curMipses.append(store_to_reg);
                            //改符号表，记录寄存器地址
                            m.operand1.reg = target;
                        }
                    }
                }else{//无初始化，分配位置
                    //为变量分配存储空间
                    String target = "";
                    if (s_num >= 8 || m.symbol_table.funcName.equals("out")) {//s已满或全局变量
                        int offset = global_offset*4;
                        //改符号表，记录地址
                        m.operand1.offset = global_offset;
                        global_offset++;
                    } else {//存到寄存器里
                        target = "$s" + s_num;
                        s_num++;
                        //改符号表，记录寄存器地址
                        m.operand1.reg = target;
                    }
                }
            }else if(m.operation.equals("=")) {//赋值语句 等号右侧要么是数值，要么是临时变量
                Symbol lval = m.operand1;
                String location = null;
                if (lval != null && lval.dimension != 0) {//a[0] = 4t
                    //数组初始化
                        if (m.operand_2.charAt(m.operand_2.length() - 1) == 't'&&Character.isDigit(m.operand_2.charAt(0))) {
                            Symbol s = m.symbol_table.lookupSymbol(m.operand_2,-1);
                            if(s.reg != null){
                                String sw = "sw " + s.reg + " " + 4 * (m.result + lval.offset) + "($gp)\n";
                                curMipses.append(sw);
                            }else if(s.offset != -1){
                                String r = alloc_treg();
                                String lw = "lw " + r + " " + s.offset*4 + "($gp)\n";
                                String sw = "sw " + r + " " + 4 * (m.result + lval.offset) + "($gp)\n";
                                curMipses.append(lw).append(sw);
                            }

                        } else if (m.operand2 != null) {//a[0] = c
                            if (m.operand2.reg != null) {
                                String sw = "sw " + m.operand2.reg + " " + 4 * (m.result + lval.offset) + "($gp)\n";
                                curMipses.append(sw);
                            } else if (m.operand2.offset != -1) {
                                String reg = alloc_treg();
                                String lw = "lw " + reg + " " + 4 * m.operand2.offset + "($gp)\n";
                                String sw = "sw " + reg + " " + 4 * (m.result + lval.offset) + "($gp)\n";
                                curMipses.append(lw).append(sw);
                            } else {
                                String reg = alloc_treg();
                                String li = "li " + reg + " " + m.operand2.value + "\n";
                                String sw = "sw " + reg + " " + 4 * (m.result + lval.offset) + "($gp)\n";
                                curMipses.append(li).append(sw);
                            }
                        } else {//a[2] = 5
                            String reg = alloc_treg();
                            String li = "li " + reg + " " + Integer.parseInt(m.operand_2) + "\n";
                            String sw = "sw " + reg + " " + 4 * (m.result + lval.offset) + "($gp)\n";
                            curMipses.append(li).append(sw);
                        }


                }else if(lval != null) {//给变量赋值
                    String source = "$t" + ((t_num + 9) % 10);//假定是表达式，找到表达式的存储位置(等价于（t-1）%10)
                    if (m.operand_2.charAt(m.operand_2.length() - 1) != 't'||!Character.isDigit(m.operand_2.charAt(0))) {
                        //如果不是临时寄存器，需要现场分配一个临时寄存器
                        source = alloc_treg();
                        String exp = "";
                        if (m.operand2 != null) {// b = a
                            exp = m.operand2.reg;
                            if (exp == null && m.operand2.offset != -1) {//内存取值
                                String lw = "lw " + source + " " + m.operand2.offset * 4 + "($gp)\n";
                                curMipses.append(lw);
                            } else if (exp != null) {//寄存器取值
                                String move_reg = "move " + source + " " + exp + "\n";
                                curMipses.append(move_reg);
                            } else {//符号表取值
                                String li = "li " + source + " " + m.operand2.value + "\n";
                                curMipses.append(li);
                            }
                        } else {//b = 2
                            exp = m.operand_2;
                            String li = "li " + source + " " + exp + "\n";
                            curMipses.append(li);
                        }
                    }//其他情况，那source就没有问题

                    if (lval.offset < 0 && lval.reg == null) {
                        if (s_num < 8) {//变量已声明但未分配空间且s寄存器未满
                            lval.reg = "$s" + s_num++;
                            location = lval.reg;
                            //改符号表↑
                        } else {//变量已声明但未分配空间且s寄存器已满
//                            int offset = global_offset*4;
//                            String move_gp = "sw " + source + " " + offset + "($gp)\n";
//                            curMipses.append(move_gp);
                            //改符号表，记录地址
                            m.operand1.offset = global_offset;
                            global_offset++;
                        }
                    }

                    if (lval.reg == null && lval.offset != -1) {//改内存
                        location = String.valueOf(lval.offset * 4);
                        String mend_mem = "sw " + source + " " + location + "($gp)\n";
                        curMipses.append(mend_mem);
                    } else if (lval.reg != null) {//改寄存器
                        location = m.operand1.reg;
                        String mend_reg = "move " + location + " " + source + "\n";
                        curMipses.append(mend_reg);
                    } else {//改符号表

                    }
                }
                else if(m.symbol_table.lookupSymbol(m.operand_1,m.use_line)!=null&&m.operand_1!=null&&m.operand_1.charAt(m.operand_1.length() - 1)=='t'&&Character.isDigit(m.operand_1.charAt(0))){//数组中某个元素赋值语句
                    String temp = alloc_treg();
                    lval = m.symbol_table.lookupSymbol(m.operand_1,m.use_line);
                    String lw = "lw " + temp + " " + lval.offset * 4 + "($gp)\n";
                    curMipses.append(lw);

                    if (m.operand_2.charAt(m.operand_2.length() - 1) == 't'&&Character.isDigit(m.operand_2.charAt(0))) {//xt
                        String reg = xt2$tx.get(m.operand_2);
                        if(reg == null) {
                            m.operand2 = m.symbol_table.lookupSymbol(m.operand_2, m.use_line);
                            if (m.operand2.reg != null) {//寄存器
                                reg = m.operand2.reg;
                                String mul = "mul " + temp + " " + temp + " 4\n";
                                String mov_gp = "add $gp $gp " + temp + "\n";
                                String sw = "sw " + reg + " 0($gp)\n";
                                String back_gp = "sub $gp $gp " + temp + "\n";
                                curMipses.append(mul).append(mov_gp).append(sw).append(back_gp);
                            } else if (m.operand2.offset != -1) {//内存
                                reg = alloc_treg();
                                String mul = "mul " + temp + " " + temp + " 4\n";
                                String lw_1 = "lw " + reg + " " + m.operand2.offset * 4 + "($gp)\n";
                                String mov_gp = "add $gp $gp " + temp + "\n";
                                String sw = "sw " + reg + " 0($gp)\n";
                                String back_gp = "sub $gp $gp " + temp + "\n";
                                curMipses.append(mul).append(lw_1).append(mov_gp).append(sw).append(back_gp);
                            }
                        }else {
                                String mul = "mul " + temp + " " + temp + " 4\n";
                                String mov_gp = "add $gp $gp " + temp + "\n";
                                String sw = "sw " + reg + " 0($gp)\n";
                                String back_gp = "sub $gp $gp " + temp + "\n";
                                curMipses.append(mul).append(mov_gp).append(sw).append(back_gp);

                        }
                    }else if (m.operand2 != null) {//var
                        if (m.operand2.reg != null) {//寄存器
                            String reg = m.operand2.reg;
                            String mul = "mul " + temp + " " + temp + " 4\n";
                            String mov_gp = "add $gp $gp " + temp + "\n";
                            String sw = "sw " + reg + " 0($gp)\n";
                            String back_gp = "sub $gp $gp " + temp + "\n";
                            curMipses.append(mul).append(mov_gp).append(sw).append(back_gp);
                        } else if (m.operand2.offset != -1) {//内存
                            String reg = alloc_treg();
                            String mul = "mul " + temp + " " + temp + " 4\n";
                            String lw_1 = "lw " + reg + " " + m.operand2.offset*4 + "($gp)\n";
                            String mov_gp = "add $gp $gp " + temp + "\n";
                            String sw = "sw " + reg + " 0($gp)\n";
                            String back_gp = "sub $gp $gp " + temp + "\n";
                            curMipses.append(mul).append(lw_1).append(mov_gp).append(sw).append(back_gp);
                        } else {//常量
                            String reg = alloc_treg();
                            String mul = "mul " + temp + " " + temp + " 4\n";
                            String li = "li " + reg + " " + m.operand2.value + "\n";
                            String mov_gp = "add $gp $gp " + temp + "\n";
                            String sw = "sw " + reg + " 0($gp)\n";
                            String back_gp = "sub $gp $gp " + temp + "\n";
                            curMipses.append(mul).append(li).append(mov_gp).append(sw).append(back_gp);
                        }

                    } else {//value
                        String reg = alloc_treg();
                        String mul = "mul " + temp + " " + temp + " 4\n";
                        String li = "li " + reg + " " + m.operand_2 + "\n";
                        String mov_gp = "add $gp $gp " + temp + "\n";
                        String sw = "sw " + reg + " 0($gp)\n";
                        String back_gp = "sub $gp $gp " + temp + "\n";
                        curMipses.append(mul).append(li).append(mov_gp).append(sw).append(back_gp);
                    }
                }
                else if(m.operand_2.charAt(m.operand_2.length()-1)!='t'){//push 3
                    String target = alloc_treg();
                    String li = "li " + target + " " + m.operand_2+"\n";
                    curMipses.append(li);
                }else if(m.operand_2.charAt(m.operand_2.length()-1)=='t'){//xxxx = 6t
                    if(s_num >= 8){//分配内存
                        String sw = "sw $t" + (t_num+9)%10 + " " + global_offset*4 + "($gp)\n";
                        curMipses.append(sw);
                        //加入符号表
                        Symbol new_symbol = new Symbol(m.operand_2,"var",0,m.use_line);
                        new_symbol.offset = global_offset++;
                        m.symbol_table.symbols.add(new_symbol);
                    }else{//分配寄存器
                        String target = alloc_sreg();
                        String move = "move " + target + " $t" + (t_num+9)%10 + "\n";
                        curMipses.append(move);
                        //加入符号表
                        Symbol new_symbol = new Symbol(m.operand_2,"var",0,m.use_line);
                        new_symbol.reg = target;
                        m.symbol_table.symbols.add(new_symbol);

                    }
                }

                //改符号表(不用改)
            }else if(m.operation.equals("+")){
                handleCal(m,"+",curMipses);
            }else if(m.operation.equals("-")){
                handleCal(m,"-",curMipses);
            }else if(m.operation.equals("*")){
                handleCal(m,"*",curMipses);
            }else if(m.operation.equals("/")){
                handleCal(m,"/",curMipses);
            }else if(m.operation.equals("%")){
                handleCal(m,"%",curMipses);
            }else if(m.operation.equals("!")){
                handleCal(m,"!",curMipses);
            }else if(m.operation.equals("scanf")){
                String li = "li $v0 5\n";
                String sys = "syscall\n";
                curMipses.append(li).append(sys);

                if(m.operand1 == null){
                    m.operand1 = m.symbol_table.lookupSymbol(m.operand_1,m.use_line);

                    //修改对应值
                    if(m.operand1.reg!=null){//改寄存器
                        String t = alloc_treg();
                        String getReg = "move " + t + " " + m.operand1.reg + "\n";
                        String move_reg = "move " + m.operand1.reg + " $v0\n";
                        curMipses.append(move_reg);
                    }else{//改内存
                        String t = alloc_treg();
                        String lw = "lw " + t + " " + m.operand1.offset*4 + "($gp)\n";
                        String mul = "mul " + t + " " + t + " 4\n";
                        String mov_gp = "add $gp $gp " + t + "\n";
                        String mend_mem = "sw $v0 0($gp)\n";
                        String back_gp = "sub $gp $gp " + t + "\n";
                        curMipses.append(lw).append(mul).append(mov_gp).append(mend_mem).append(back_gp);
                    }
                }else{
                    //如果变量未初始化，需要分配地址并修改符号(此时尽管是简单数值，但也无法仅存在符号表里了，读入的值没法和前端交互)
                    if(m.operand1.offset == -1 && m.operand1.reg == null){
                        if(s_num < 8){//分配s寄存器
                            m.operand1.reg = alloc_sreg();
                        }else{//分配内存
                            m.operand1.offset = global_offset++;
                        }
                    }

                    //修改对应值
                    if(m.operand1.reg!=null){//改寄存器
                        String move_reg = "move " + m.operand1.reg + " $v0\n";
                        curMipses.append(move_reg);
                    }else{//改内存
                        String mend_mem = "sw $v0 " + m.operand1.offset*4 + "($gp)\n";
                        curMipses.append(mend_mem);
                    }
                }

            }else if(m.operation.equals("printf %d")){
                //将表达式的结果直接放到$a0中等下直接输出
                String res = "";
//                if(m.operand_1.charAt(m.operand_1.length()-1)=='t'){//如果输出的是中间结果，必在寄存器里
////                    res = "$t" + (t_num+9)%10;
//                    res = xt2$tx.get(m.operand_1);
//                    if(res == null) res = "$t" + (t_num+9)%10;
//                    String mov_reg = "move $a0 " + res + "\n";
//                    curMipses.append(mov_reg);
//                }else{
                    if(m.symbol_table.lookupSymbol(m.operand_1,m.use_line)!=null){
                        res = m.symbol_table.lookupSymbol(m.operand_1,m.use_line).reg;//如果要输出的变量存在寄存器里
                        if(res == null&&m.symbol_table.lookupSymbol(m.operand_1,m.use_line).offset!=-1){//如果在内存,就直接放到a0
                            String lw = "lw $a0 " + m.symbol_table.lookupSymbol(m.operand_1,m.use_line).offset*4 + "($gp)\n";
                            curMipses.append(lw);
                        }else if(res == null){//值存在符号表里
                            String li = "li $a0 " + m.symbol_table.lookupSymbol(m.operand_1,m.use_line).value + "\n";
                            curMipses.append(li);
                        }else{
                            String mov_reg = "move $a0 " + res + "\n";
                            curMipses.append(mov_reg);
                        }
                    }else if(m.symbol_table.lookupSymbol(m.operand_1,-8)!=null) {
                        res = m.symbol_table.lookupSymbol(m.operand_1,-8).reg;//如果要输出的变量存在寄存器里
                        if(res == null&&m.symbol_table.lookupSymbol(m.operand_1,-8).offset!=-1){//如果在内存,就直接放到a0
                            String lw = "lw $a0 " + m.symbol_table.lookupSymbol(m.operand_1,-8).offset*4 + "($gp)\n";
                            curMipses.append(lw);
                        }else if(res == null){//值存在符号表里
                            String li = "li $a0 " + m.symbol_table.lookupSymbol(m.operand_1,-8).value + "\n";
                            curMipses.append(li);
                        }else{
                            String mov_reg = "move $a0 " + res + "\n";
                            curMipses.append(mov_reg);
                        }
                    }else{//printf("%d",8989);
                            String li = "li $a0 " + m.operand_1 + "\n";
                            curMipses.append(li);
                        }

//                }

                String li = "li $v0 1\n";
                String sys = "syscall\n";
                curMipses.append(li).append(sys);
            }else if(m.operation.equals("printf const str")){
                //加入字符串data条目
                DataEntry str = new DataEntry();
                str.str_ident = m.operand_1;
                str.str_content = m.res;
                datas.add(str);
                //字符串首地址放到$a0
                String la = "la $a0 " + m.operand_1 + "\n";
                String li = "li $v0 4\n";
                String sys = "syscall\n";
                curMipses.append(la).append(li).append(sys);
            }else if(m.operation.equals("return")){
                if(m.operand_1!=null){//有返回值
                    if(m.res == null){//返回值是一个常数值
                        String li = "li $v0 " + m.result + "\n";
                        curMipses.append(li);
                    }else{//返回值是局部变量或临时变量
                        if(m.operand1 != null){//局部变量
                            if(m.operand1.reg != null){
                                String mov_reg = "move $v0 " + m.operand1.reg + "\n";
                                curMipses.append(mov_reg);
                            }else{
                                String lw = "lw $v0 " + m.operand1.offset*4 + "($gp)\n";
                                curMipses.append(lw);
                            }
                        }else {//临时变量
                            String reg = "$t" + (t_num + 9)%10;
                            String mov_reg = "move $v0 " + reg + "\n";
                            curMipses.append(mov_reg);
                        }
                    }

                }

                String jr = "jr $ra\n";
                curMipses.append(jr);
            }else if(m.operation.equals("func_def")){
                FuncDefEntry thisFunc = new FuncDefEntry();
                thisFunc.funcName = m.operand_1;
                curMipses = thisFunc.funcMipses;
                this.funcDefs.add(thisFunc);
            }else if(m.operation.equals("call")){
                String call = "jal " + m.res + "\n";
                curMipses.append(call);

                String take_ra = "lw $ra 0($sp)\naddi $sp $sp 4\n";
                curMipses.append(take_ra);

                //todo:取回变量 get_variable 向外层符号表一直取，直到funcname不是block了为止
                for(int it = m.symbol_table.symbols.size() - 1;it >=0;it--) {
                    Symbol s = m.symbol_table.symbols.get(it);

//                    if(s.dimension!=0&&!s.type.equals("const")){
//                        String reg = alloc_treg();
//                        if(s.formal){
//                            String lw = "lw " + reg + " " + s.offset * 4 + "($gp)\n";
//                            curMipses.append(lw);
//                        }else{
//                            String li = "li " + reg + " " + String.valueOf(s.offset) + "\n";
//                            curMipses.append(li);
//                        }
//
//
//                            if(s.dimension == 1){
//                                for(int index = s.fir_dim-1;index >= 0;index--){
//                                    String ptr = alloc_treg();
//                                    String add = "add " + ptr + " " + reg + " " + index + "\n";
//                                    String mul = "mul " + ptr + " " + ptr + " 4\n";
//                                    String mov_gp = "add $gp $gp " + ptr + "\n";
//                                    String r = alloc_treg();
//                                    String lw_1 = "lw " + r + " 0($sp)\n";
//                                    String sw = "sw " + r + " 0($gp)\n";
//                                    String back_gp = "sub $gp $gp " + ptr + "\n";
//                                    String mov_sp = "addi $sp $sp 4\n";
//                                    curMipses.append(add).append(mul).append(mov_gp).append(lw_1).append(sw).append(back_gp).append(mov_sp);
//                                }
//                            }else if(s.dimension == 2){
//                                for(int index = s.fir_dim*s.sec_dim-1;index >= 0;index--){
//                                    String ptr = alloc_treg();
//                                    String add = "add " + ptr + " " + reg + " " + index + "\n";
//                                    String mul = "mul " + ptr + " " + ptr + " 4\n";
//                                    String mov_gp = "add $gp $gp " + ptr + "\n";
//                                    String r = alloc_treg();
//                                    String lw_1 = "lw " + r + " 0($sp)\n";
//                                    String sw = "sw " + r + " 0($gp)\n";
//                                    String back_gp = "sub $gp $gp " + ptr + "\n";
//                                    String mov_sp = "addi $sp $sp 4\n";
//                                    curMipses.append(add).append(mul).append(mov_gp).append(lw_1).append(sw).append(back_gp).append(mov_sp);
//                                }
//                            }
//
//
//                    }

                    if (!s.type.equals("const")&&!s.type.equals("int")&&!s.type.equals("void")&&s.dimension == 0) {
                        if(s.reg!=null){
                            String lw = "lw " + s.reg + " 0($sp)\n";
                            curMipses.append(lw);
                        }else if(s.offset!=-1){
                            String reg = alloc_treg();
                            String lw ="lw " + reg + " 0($sp)\n";
                            String sw = "sw " + reg + " " + s.offset*4 + "($gp)\n";
                            curMipses.append(lw).append(sw);
                        }else{
                            String reg = alloc_treg();
                            String lw ="lw " + reg + " 0($sp)\n";
                            curMipses.append(lw);
                            //给分配内存或寄存器
                            if(s_num >=8 ){//分配内存
                                s.offset = global_offset++;
                                String sw = "sw " + reg + " " +s.offset*4 + "($gp)\n";
                                curMipses.append(sw);
                            }else{//分配寄存器
                                s.reg = alloc_sreg();
                                String move = "move " + s.reg + " " + reg + "\n";
                                curMipses.append(move);
                            }
                        }
                        String mov_sp = "addi $sp $sp 4\n";
                        curMipses.append(mov_sp);
                    }
//                    else if(!s.type.equals("const")&&!s.type.equals("lval")&&!s.formal){
//                        String reg = alloc_treg();
//                        String lw ="lw " + reg + " 0($sp)\n";
//                        String sw = "sw " + reg + " " + s.offset*4 + "($gp)\n";
//                        curMipses.append(lw).append(sw);
//                        String mov_sp = "addi $sp $sp 4\n";
//                        curMipses.append(mov_sp);
//                    }
                    else if(!s.type.equals("const")&&!s.type.equals("int")&&!s.type.equals("void")&&!s.type.equals("lval")&&s.formal){//形参数组,取回基地址
                        String reg = alloc_treg();
                        String lw ="lw " + reg + " 0($sp)\n";
                        String sw = "sw " + reg + " " + s.offset*4 + "($gp)\n";
                        curMipses.append(lw).append(sw);
                        String mov_sp = "addi $sp $sp 4\n";
                        curMipses.append(mov_sp);
                    }
                }
            }else if(m.operation.equals("save_variable")){
                for(Symbol s: m.symbol_table.symbols){
                    if(!s.type.equals("const")&&!s.type.equals("int")&&!s.type.equals("void")&&s.dimension == 0){//普通变量
                        String mov_sp = "subi $sp $sp 4\n";
                        if(s.reg!=null){
                            String sw = "sw " + s.reg + " 0($sp)\n";
                            curMipses.append(mov_sp).append(sw);
                        }else if(s.offset!=-1){
                            String reg = alloc_treg();
                            String lw = "lw " + reg + " " + s.offset*4 + "($gp)\n";
                            String sw = "sw " + reg + " 0($sp)\n";
                            curMipses.append(mov_sp).append(lw).append(sw);
                        }else{
                            String reg = alloc_treg();
                            String li = "li " + reg + " " + s.value + "\n";
                            String sw = "sw " + reg + " 0($sp)\n";
                            curMipses.append(mov_sp).append(li).append(sw);
                        }
                    }

                    else if(!s.type.equals("const")&&!s.type.equals("int")&&!s.type.equals("void")&&!s.type.equals("lval")&&s.formal) {//形参数组,存下基地址
                        String mov_sp = "subi $sp $sp 4\n";
                        String reg = alloc_treg();
                        String lw = "lw " + reg + " " + s.offset * 4 + "($gp)\n";
                        String sw = "sw " + reg + " 0($sp)\n";
                        curMipses.append(mov_sp).append(lw).append(sw);
                    }

//                    if(s.dimension!=0&&!s.type.equals("const")){
//
//                        String reg = alloc_treg();
//                        if(s.formal){
//                            String lw = "lw " + reg + " " + s.offset * 4 + "($gp)\n";
//                            curMipses.append(lw);
//                        }else{
//                            String li = "li " + reg + " " + String.valueOf(s.offset) + "\n";
//                            curMipses.append(li);
//                        }
//
//                            if(s.dimension == 1){
//                                for(int index = 0;index < s.fir_dim;index++){
//                                    String mov_sp = "subi $sp $sp 4\n";
//                                    curMipses.append(mov_sp);
//                                    String ptr = alloc_treg();
//                                    String add = "add " + ptr + " " + reg + " " + index + "\n";
//                                    String mul = "mul " + ptr + " " + ptr + " 4\n";
//                                    String mov_gp = "add $gp $gp " + ptr + "\n";
//                                    String r = alloc_treg();
//                                    String lw_1 = "lw " + r + " 0($gp)\n";
//                                    String back_gp = "sub $gp $gp " + ptr + "\n";
//                                    String sav_num = "sw " + r + " 0($sp)\n";
//                                    curMipses.append(add).append(mul).append(mov_gp).append(lw_1).append(back_gp).append(sav_num);
//                                }
//                            }else if(s.dimension == 2){
//                                for(int index = 0;index < s.fir_dim*s.sec_dim;index++){
//                                    String mov_sp = "subi $sp $sp 4\n";
//                                    curMipses.append(mov_sp);
//                                    String ptr = alloc_treg();
//                                    String add = "add " + ptr + " " + reg + " " + index + "\n";
//                                    String mul = "mul " + ptr + " " + ptr + " 4\n";
//                                    String mov_gp = "add $gp $gp " + ptr + "\n";
//                                    String r = alloc_treg();
//                                    String lw_1 = "lw " + r + " 0($gp)\n";
//                                    String back_gp = "sub $gp $gp " + ptr + "\n";
//                                    String sav_num = "sw " + r + " 0($sp)\n";
//                                    curMipses.append(add).append(mul).append(mov_gp).append(lw_1).append(back_gp).append(sav_num);
//                                }
//                            }
//                        }

                    }

            }else if(m.operation.equals("push")){

                if(m.symbol_table.lookupSymbol(m.operand_1,m.use_line)==null){//表达式
                    String reg = "$t" + (t_num+9)%10;
                    String mov_sp = "subi $sp $sp 4\n";
                    String sw = "sw " + reg + " 0($sp)\n";
                    curMipses.append(mov_sp).append(sw);
                }else{//变量
                    Symbol rparam = m.symbol_table.lookupSymbol(m.operand_1,m.use_line);
                    String mov_sp = "subi $sp $sp 4\n";
                    curMipses.append(mov_sp);
                    if(rparam.dimension == 0){//普通实参变量
                        if(rparam.reg == null&&rparam.offset!=-1){//内存里
                            String temporary = alloc_treg();
                            String lw = "lw " + temporary + " " + rparam.offset*4 + "($gp)\n";
                            String sw = "sw " + temporary + " 0($sp)\n";
                            curMipses.append(lw).append(sw);
                        }else if(rparam.reg!=null){//寄存器里
                            String sw = "sw " + rparam.reg + " 0($sp)\n";
                            curMipses.append(sw);
                        }else{//符号表里
                            String temporary = alloc_treg();
                            String li = "li " + temporary + " " +rparam.value + "\n";
                            curMipses.append(li);
                            String sw = "sw " + temporary + " 0($sp)\n";
                            curMipses.append(sw);
                        }
                    }else{//数组变量，传地址
                        if(!rparam.formal){
                            String reg = alloc_treg();
                            String li = "li " + reg + " " + rparam.offset + "\n";
                            String sw = "sw " + reg + " 0($sp)\n";
                            curMipses.append(li).append(sw);
                        }else{
                            String reg = alloc_treg();
                            String lw = "lw " + reg + " " + rparam.offset*4 + "($gp)\n";
                            String sw = "sw " + reg + " 0($sp)\n";
                            curMipses.append(lw).append(sw);
                        }

                    }

                }
            }else if(m.operation.equals("para")){
                if(s_num < 8&&m.operand1.dimension == 0){
                    String target = alloc_sreg();
                    String pop_sp = "lw " + target + " 0($sp)\n";
                    String mov_sp = "addi $sp $sp 4\n";
                    curMipses.append(pop_sp);
                    curMipses.append(mov_sp);
                    //改符号表
                    m.operand1.reg = target;
                    m.operand1.formal = true;
                }
//                else if(m.operand1.dimension >= 1){
//                    String temporary = alloc_treg();
//                    String pop_sp = "lw " + temporary + " 0($sp)\n";
//                    String mov_sp = "addi $sp $sp 4\n";
//                    curMipses.append(pop_sp);
//                    curMipses.append(mov_sp);
//
//                }
                else{
                        String temporary = alloc_treg();
                        String pop_sp = "lw " + temporary + " 0($sp)\n";
                        String mov_sp = "addi $sp $sp 4\n";
                        curMipses.append(pop_sp);
                        curMipses.append(mov_sp);
                        String sw = "sw " + temporary +" " + global_offset*4 + "($gp)\n";
                        curMipses.append(sw);
                        //改符号表
                        m.operand1.offset = global_offset++;
                        m.operand1.formal = true;

                }
            }else if(m.operation.equals("main")){
                curMipses = this.mipses;
                curMipses.append("main:\n");
            }else if(m.operation.equals("ra")){
                String push_ra = "subi $sp $sp 4\nsw $ra 0($sp)\n";
                curMipses.append(push_ra);
            }else if(m.operation.equals("==")){
                //保存返回值并不会增加通过的样例个数
//                Symbol s = new Symbol(m.operand_1,"var",0,m.use_line,1);
                String target = alloc_treg();
                String save_ret = "move " + target + " $v0\n";
                curMipses.append(save_ret);
//                String sw = "sw " + target + " " + global_offset*4 + "($gp)\n";
//                curMipses.append(sw);
//                s.offset= global_offset++;
//                m.symbol_table.symbols.add(s);
            }else if(m.operation.equals("[][]")||m.operation.equals("save [][]")){//二维数组取值或存值
                arr++;
            }else if(m.operation.equals("[value]")||m.operation.equals("[xt]")||m.operation.equals("[var]")){
                handelArray(m,curMipses);
            }else if(m.operation.equals("++")){//地址加偏移 operand2是数组符号
                if(!m.operand2.formal){//非形参，offset就是地址首地址
                    m.operand_2 = String.valueOf(m.operand2.offset);
                    m.operation = "+";
                    handleCal(m,"+",curMipses);
                }else{//形参，offset需要取一下内存才是首地址
                    m.operand_2 = String.valueOf(m.operand2.offset);
                    String temp = alloc_treg();
                    String lw1 = "lw " +temp + " " + m.operand2.offset*4 + "($gp)\n";//temp放地址首地址
                    curMipses.append(lw1);
                    m.operand2 = null;
                    m.operand_2 = temp;
                    m.operation = "+";
                    handleCal(m,"+",curMipses);
                }

            }else if(m.operation.equals("save [value]")||m.operation.equals("save [xt]")||m.operation.equals("save [var]")){
                changeArray(m,curMipses);
            }else if(m.operation.equals("label")){
                String label = m.res + ":\n";
                curMipses.append(label);
            }else if(m.operation.equals("jump_without_condition")){
                String j = "j " + m.res + "\n";
                curMipses.append(j);
            }else if(m.operation.equals("jump")){
                String reg = xt2$tx.get(m.res);
                if(m.res.charAt(m.res.length()-1)!='t'&&m.res.charAt(0)!='$'){
                    if(m.symbol_table.lookupSymbol(m.res,m.use_line)!=null){// var
                        Symbol s = m.symbol_table.lookupSymbol(m.res,m.use_line);
                        if(s.offset!=-1){
                            String r = alloc_treg();
                            String lw = "lw " + r + " " + s.offset*4 + "($gp)\n";
                            curMipses.append(lw);
                            reg = r;
                        }else if(s.reg != null){
                            reg= s.reg;
                        }else{
                            String r = alloc_treg();
                            String li = "li " + r + " " + s.value + "\n";
                            curMipses.append(li);
                            reg = r;
                        }
                    }else{
                        int val = Integer.valueOf(m.res);
                        String r = alloc_treg();
                        if(val != 0){
                            String li = "li " + r + " 1\n";
                            curMipses.append(li);
                        }else{
                            String li = "li " + r + " 0\n";
                            curMipses.append(li);
                        }
                        reg = r;
                    }
                }else{
                    if(reg == null){
                        assert m.symbol_table.lookupSymbol(m.res,m.use_line)!=null;
                        reg = m.symbol_table.lookupSymbol(m.res,m.use_line).reg;
                        if(reg == null&&m.symbol_table.lookupSymbol(m.res,m.use_line).offset!=-1){
                            reg = alloc_treg();
                            String lw = "lw " + reg + " " + m.symbol_table.lookupSymbol(m.res,m.use_line).offset*4 + "($gp)\n";
                            curMipses.append(lw);
                        }
                        else if(reg != null){

                        }
                        else{//value
                            reg = alloc_treg();
                            String li = "li " + reg + " " + m.symbol_table.lookupSymbol(m.res,m.use_line).value + "\n";
                            curMipses.append(li);
                        }
                    }
                }

                if(m.result == 1){
                    String sigmoid = "sne " + reg + " " + reg + " 0\n";
                    curMipses.append(sigmoid);
                }
                String beq = "beq " + reg + " " + m.result + " " + m.operand_2 + "\n";
                curMipses.append(beq);
            }else if(m.operation.equals("<")){
                branch(m,curMipses);
            }else if(m.operation.equals("<=")){
                branch(m,curMipses);
            }else if(m.operation.equals(">")){
                branch(m,curMipses);
            }else if(m.operation.equals(">=")){
                branch(m,curMipses);
            }else if(m.operation.equals("===")){
               branch(m,curMipses);
            }else if(m.operation.equals("!=")){
                branch(m,curMipses);
            }else if(m.operation.equals("< cond")){
                handleCal(m,"-",curMipses);
                String slti = "slti " + m.res + " " + m.res + " 0\n";
                curMipses.append(slti);
            }else if(m.operation.equals("<= cond")){
                handleCal(m,"-",curMipses);
                String sle = "sle " + m.res + " " + m.res + " 0\n";
                curMipses.append(sle);
            }else if(m.operation.equals("> cond")){
                handleCal(m,"-",curMipses);
                String sgt = "sgt " + m.res + " " + m.res + " 0\n";
                curMipses.append(sgt);
            }else if(m.operation.equals(">= cond")){
                handleCal(m,"-",curMipses);
                String sge = "sge " + m.res + " " + m.res + " 0\n";
                curMipses.append(sge);
            }else if(m.operation.equals("== cond")){
                handleCal(m,"-",curMipses);
                String seq = "seq " + m.res + " " + m.res + " 0\n";
                curMipses.append(seq);
            }else if(m.operation.equals("!= cond")){
                handleCal(m,"-",curMipses);
                String sne = "sne " + m.res + " " + m.res + " 0\n";
                curMipses.append(sne);
            }
        }
    }
    private void branch(MidCode m,StringBuilder curMipses){
        String left = xt2$tx.get(m.operand_1);
        String right = xt2$tx.get(m.operand_2);
        if(m.operand_1.charAt(m.operand_1.length()-1)!='t'&&m.operand_1.charAt(0)!='$'){
            if(m.symbol_table.lookupSymbol(m.operand_1,m.use_line)!=null){// var
                Symbol s = m.symbol_table.lookupSymbol(m.operand_1,m.use_line);
                if(s.offset!=-1){
                    String r = alloc_treg();
                    String lw = "lw " + r + " " + s.offset*4 + "($gp)\n";
                    curMipses.append(lw);
                    left = r;
                }else if(s.reg != null){
                    left = s.reg;
                }else{
                    String r = alloc_treg();
                    String li = "li " + r + " " + s.value + "\n";
                    curMipses.append(li);
                    left = r;
                }
            }else{
                int val = Integer.valueOf(m.operand_1);
                String r = alloc_treg();
//                if(val != 0){
//                    String li = "li " + r + " 1\n";
//                    curMipses.append(li);
//                }else{
//                    String li = "li " + r + " 0\n";
//                    curMipses.append(li);
//                }
                String li = "li " + r + " " + m.operand_1 + "\n";
                curMipses.append(li);
                left = r;
            }

        }else {
            if (left == null) {
                assert m.symbol_table.lookupSymbol(m.operand_1, m.use_line) != null;
                left = m.symbol_table.lookupSymbol(m.operand_1, m.use_line).reg;
                if (left == null) {
                    left = alloc_treg();
                    String lw = "lw " + left + " " + m.symbol_table.lookupSymbol(m.operand_1, m.use_line).offset * 4 + "($gp)\n";
                    curMipses.append(lw);
                }
            }
        }

        if(m.operand_2.charAt(m.operand_2.length()-1)!='t'&&m.operand_2.charAt(0)!='$'){
            if(m.symbol_table.lookupSymbol(m.operand_2,m.use_line)!=null) {// var
                Symbol s = m.symbol_table.lookupSymbol(m.operand_2,m.use_line);
                if(s.offset!=-1){
                    String r = alloc_treg();
                    String lw = "lw " + r + " " + s.offset*4 + "($gp)\n";
                    curMipses.append(lw);
                    right = r;
                }else if(s.reg != null){
                    right = s.reg;
                }else{
                    right = String.valueOf(s.value);
                }
            }else{
                right = m.operand_2;
            }
        }else {
            if (right == null) {
                assert m.symbol_table.lookupSymbol(m.operand_2, m.use_line) != null;
                right = m.symbol_table.lookupSymbol(m.operand_2, m.use_line).reg;
                if (right == null) {
                    right = alloc_treg();
                    String lw = "lw " + right + " " + m.symbol_table.lookupSymbol(m.operand_2, m.use_line).offset * 4 + "($gp)\n";
                    curMipses.append(lw);
                }
            }
        }
        String instr = symbol2instr.get(m.operation);
        String branch = instr + " " + left + " " + right + " " + m.res + "\n";
        curMipses.append(branch);
    }
    private void changeArray(MidCode m,StringBuilder curMipses){
        if(arr == 0){//一维数组修改某个元素的值
            String reg = alloc_treg();//先整个寄存器用来放偏移量
            if(m.operation.equals("save [value]")){
                String li = "li " + reg + " " +m.result + "\n";
                curMipses.append(li);
                if(m.operand1.formal){
                    saveFormalMem(m,reg,curMipses);
                }else{
                    saveMem(m,reg,curMipses);
                }
            }else if(m.operation.equals("save [xt]")){
                Symbol s = m.symbol_table.lookupSymbol(m.res,m.use_line);
                if(s.offset != -1){
                    String t = alloc_treg();
                    String lw = "lw " + t + " " + s.offset*4 + "($gp)\n";
                    String add = "add " + reg + " $0 " + t + "\n";
                    curMipses.append(lw).append(add);
                }else if(s.reg != null){
                    String add = "add " + reg + " $0 " + s.reg + "\n";
                    curMipses.append(add);
                }

                if(m.operand1.formal){
                    saveFormalMem(m,reg,curMipses);
                }else{
                    saveMem(m,reg,curMipses);
                }
            }else if(m.operation.equals("save [var]")){
                if(m.operand2.reg != null){//寄存器
                    String add = "add " + reg + " " + m.operand2.reg + " $0\n";
                    curMipses.append(add);
                }else if(m.operand2.offset != -1){//内存
                    String temp = alloc_treg();
                    String lw = "lw " + temp + " " + m.operand2.offset*4 + "($gp)\n";
                    String add = "add " + reg + " $0 " + temp + "\n";
                    curMipses.append(lw).append(add);
                }

                if(m.operand1.formal){
                    saveFormalMem(m,reg,curMipses);
                }else{
                    saveMem(m,reg,curMipses);
                }
            }
        }else if(arr == 1){
            int sec_dim = m.operand1.sec_dim;
            String reg = alloc_treg();
            if(m.operation.equals("save [value]")){
                int offset = m.result * sec_dim;
                String li = "li " + reg + " " +offset + "\n";
                curMipses.append(li);
            }else if(m.operation.equals("save [xt]")){
                String t = xt2$tx.get(m.res);
                String mul = "mul " + reg + " " + t + " " + sec_dim + "\n";
                curMipses.append(mul);
            }else if(m.operation.equals("save [var]")){
                if(m.operand2.reg == null){//内存
                    String lw = "lw " + reg + " " + m.operand2.offset*4 + "($gp)\n";
                    String mul = "mul " + reg + " " + reg + " " + sec_dim + "\n";
                    curMipses.append(lw).append(mul);
                }else if(m.operand2.offset == -1){//寄存器
                    String mul = "mul " + reg + " " + m.operand2.reg + " " + sec_dim + "\n";
                    curMipses.append(mul);
                }
            }

            arr++;
        }else if(arr == 2){
            arr = 0;

            String reg = "$t" + (t_num + 9)%10;//第一维×第二维大小的结果放在reg里
            if(m.operation.equals("save [value]")){
                String add = "add " + reg + " " + reg + " " + m.result + "\n";
                curMipses.append(add);

                if(m.operand1.formal){
                    saveFormalMem(m,reg,curMipses);
                }else{
                    saveMem(m,reg,curMipses);
                }
            }else if(m.operation.equals("save [xt]")){
                Symbol s = m.symbol_table.lookupSymbol(m.res,m.use_line);
                if(s.offset != -1){
                    String t = alloc_treg();
                    String lw = "lw " + t + " " + s.offset*4 + "($gp)\n";
                    String add = "add " + reg + " " + reg + " " + t + "\n";
                    curMipses.append(lw).append(add);
                }else if(s.reg != null){
                    String add = "add " + reg + " " + reg + " " + s.reg + "\n";
                    curMipses.append(add);
                }

                if(m.operand1.formal){
                    saveFormalMem(m,reg,curMipses);
                }else{
                    saveMem(m,reg,curMipses);
                }
            }else if(m.operation.equals("save [var]")){
                if(m.operand2.reg == null){//内存
                    String temp = alloc_treg();
                    String lw = "lw " + temp + " " + m.operand2.offset*4 + "($gp)\n";
                    String add = "add " + reg + " " + reg + " " + temp + "\n";
                    curMipses.append(lw).append(add);
                }else if(m.operand2.offset == -1){//寄存器
                    String add = "add " + reg + " " + m.operand2.reg + " " + reg + "\n";
                    curMipses.append(add);
                }

                if(m.operand1.formal){
                    saveFormalMem(m,reg,curMipses);
                }else{
                    saveMem(m,reg,curMipses);
                }
            }

        }

    }

    private void handelArray(MidCode m,StringBuilder curMipses){
        if(arr == 0){
            if(m.operation.equals("[value]")){
                if(m.operand1.formal){
                    String reg = alloc_treg();
                    String lw = "lw " + reg + " " + 4*m.operand1.offset + "($gp)\n";//reg存放数组基地址
                    String addi = "addi " + reg + " " + reg + " " + m.result + "\n";
                    String mul = "mul " + reg + " " + reg + " 4\n";
                    String mov_gp = "add $gp $gp " + reg + "\n";
                    String loc = alloc_treg();
                    String lw_1 = "lw " + loc + " 0($gp)\n";
                    String back_gp = "sub $gp $gp " + reg + "\n";
                    xt2$tx.put(m.res,loc);
                    curMipses.append(lw).append(addi).append(mul).append(mov_gp).append(lw_1).append(back_gp);
                }else{
                    String reg = alloc_treg();
                    String lw = "lw " + reg + " " + 4*(m.result + m.operand1.offset) + "($gp)\n";
                    xt2$tx.put(m.res,reg);
                    curMipses.append(lw);
                }

            }else if(m.operation.equals("[xt]")){
                if(m.operand1.formal){
                    String reg = alloc_treg();
                    String off_reg = xt2$tx.get(m.res);//xt的存储位置
                    String lw = "lw " + reg + " " + 4*m.operand1.offset + "($gp)\n";//reg存放数组基地址
                    String add = "add " + reg + " " + off_reg + " " + reg + "\n";
                    String mul = "mul " + reg + " " + reg + " 4\n";
                    String mov_gp = "add $gp $gp " + reg + "\n";
                    String loc = alloc_treg();
                    String lw_1 = "lw " + loc + " 0($gp)\n";
                    String back_gp = "sub $gp $gp " + reg + "\n";
                    xt2$tx.put(m.operand_2,loc);
                    curMipses.append(lw).append(add).append(mul).append(mov_gp).append(lw_1).append(back_gp);
                }else{
                    String reg = alloc_treg();
                    String off_reg = xt2$tx.get(m.res);//xt的存储位置
                    if(off_reg == null){
                        off_reg = alloc_treg();
                        String lw = "lw " + off_reg + " " + m.symbol_table.lookupSymbol(m.res,-9).offset*4 + "($gp)\n";
                        curMipses.append(lw);
                    }
                    String add = "addi " + reg + " " + off_reg + " " + m.operand1.offset + "\n";
                    String mul = "mul " + reg + " " + reg + " 4\n";
                    String mov_gp = "add $gp $gp " + reg + "\n";
                    String loc = alloc_treg();
                    String lw = "lw " + loc + " 0($gp)\n";
                    String back_gp = "sub $gp $gp " + reg + "\n";
                    xt2$tx.put(m.operand_2,loc);
                    curMipses.append(add).append(mul).append(mov_gp).append(lw).append(back_gp);
                }

            }else if(m.operation.equals("[var]")){
                if(m.operand1.formal){
                    String reg = alloc_treg();
                    String lw = "lw " + reg + " " + 4*m.operand1.offset + "($gp)\n";//reg存放数组基地址
                    if(m.operand2.reg == null){//内存
                        String off_reg = alloc_treg();
                        String lw_1 = "lw " + off_reg + " " + m.operand2.offset*4 + "($gp)\n";//取偏移
                        String add = "add " + reg + " " + off_reg + " " + reg + "\n";
                        String mul = "mul " + reg + " " + reg + " 4\n";
                        String mov_gp = "add $gp $gp " + reg + "\n";
                        String loc = alloc_treg();
                        String lw_2 = "lw " + loc + " 0($gp)\n";//取实际值
                        String back_gp = "sub $gp $gp " + reg + "\n";
                        xt2$tx.put(m.operand_2,loc);
                        curMipses.append(lw).append(lw_1).append(add).append(mul).append(mov_gp).append(lw_2).append(back_gp);
                    }else if(m.operand2.offset == -1){//寄存器
                        String add = "add " + reg + " " + m.operand2.reg + " " + reg + "\n";
                        String mul = "mul " + reg + " " + reg + " 4\n";
                        String mov_gp = "add $gp $gp " + reg + "\n";
                        String loc = alloc_treg();
                        String lw_2 = "lw " + loc + " 0($gp)\n";//取实际值
                        String back_gp = "sub $gp $gp " + reg + "\n";
                        xt2$tx.put(m.operand_2,loc);
                        curMipses.append(lw).append(add).append(mul).append(mov_gp).append(lw_2).append(back_gp);
                    }
                }else{
                    if(m.operand2.reg == null){//内存
                        String reg = alloc_treg();
                        String off_reg = alloc_treg();

                        String lw_1 = "lw " + off_reg + " " + m.operand2.offset*4 + "($gp)\n";
                        String add = "addi " + reg + " " + off_reg + " " + m.operand1.offset + "\n";
                        String mul = "mul " + reg + " " + reg + " 4\n";
                        String mov_gp = "add $gp $gp " + reg + "\n";
                        String loc = alloc_treg();
                        String lw = "lw " + loc + " 0($gp)\n";
                        String back_gp = "sub $gp $gp " + reg + "\n";
                        xt2$tx.put(m.operand_2,loc);
                        curMipses.append(lw_1).append(add).append(mul).append(mov_gp).append(lw).append(back_gp);
                    }else if(m.operand2.offset == -1){//寄存器
                        String reg = alloc_treg();
                        String add = "addi " + reg + " " + m.operand2.reg + " " + m.operand1.offset + "\n";
                        String mul = "mul " + reg + " " + reg + " 4\n";
                        String mov_gp = "add $gp $gp " + reg + "\n";
                        String loc = alloc_treg();
                        String lw = "lw " + loc + " 0($gp)\n";
                        String back_gp = "sub $gp $gp " + reg + "\n";
                        xt2$tx.put(m.operand_2,loc);
                        curMipses.append(add).append(mul).append(mov_gp).append(lw).append(back_gp);
                    }
                }
            }
        }else if(arr == 1){
            int sec_dim = m.operand1.sec_dim;
            String reg = alloc_treg();
            if(m.operation.equals("[value]")){
                int offset = m.result * sec_dim;
                String li = "li " + reg + " " +offset + "\n";
                curMipses.append(li);
            }else if(m.operation.equals("[xt]")){
                String t = xt2$tx.get(m.res);
                String mul = "mul " + reg + " " + t + " " + sec_dim + "\n";
                curMipses.append(mul);
            }else if(m.operation.equals("[var]")){
                if(m.operand2.reg == null){//内存
                    String lw = "lw " + reg + " " + m.operand2.offset*4 + "($gp)\n";
                    String mul = "mul " + reg + " " + reg + " " + sec_dim + "\n";
                    curMipses.append(lw).append(mul);
                }else if(m.operand2.offset == -1){//寄存器
                    String mul = "mul " + reg + " " + m.operand2.reg + " " + sec_dim + "\n";
                    curMipses.append(mul);
                }
            }

            arr ++;
        }else if(arr == 2){
            arr = 0;

            String reg = "$t" + (t_num + 9)%10;//第一维×第二维大小的结果放在reg里
            if(m.operation.equals("[value]")){
                String add = "add " + reg + " " + reg + " " + m.result + "\n";
                curMipses.append(add);

                if(m.operand1.formal){
                    getFormalMem(m,reg,curMipses);
                }else{
                    getMem(m,reg,curMipses);
                }
            }else if(m.operation.equals("[xt]")){
                String t = xt2$tx.get(m.res);
                String add = "add " + reg + " " + reg + " " + t + "\n";
                curMipses.append(add);

                if(m.operand1.formal){
                    getFormalMem(m,reg,curMipses);
                }else{
                    getMem(m,reg,curMipses);
                }
            }else if(m.operation.equals("[var]")){
                if(m.operand2.reg == null){//内存
                    String temp = alloc_treg();
                    String lw = "lw " + temp + " " + m.operand2.offset*4 + "($gp)\n";
                    String add = "add " + reg + " " + reg + " " + temp + "\n";
                    curMipses.append(lw).append(add);
                }else if(m.operand2.offset == -1){//寄存器
                    String add = "add " + reg + " " + m.operand2.reg + " " + reg + "\n";
                    curMipses.append(add);
                }

                if(m.operand1.formal){
                    getFormalMem(m,reg,curMipses);
                }else{
                    getMem(m,reg,curMipses);
                }
            }

        }
    }
    private void saveMem(MidCode m,String reg,StringBuilder curMipses){//为lval记录指向地址
        String a = "add " + reg + " " + reg + " " + m.operand1.offset + "\n";
        curMipses.append(a);
        Symbol s = new Symbol(m.operand_2,"lval",m.operand1.dimension,m.use_line,1);//为lval的指向地址设置符号
        String sw = "sw " + reg + " " + global_offset*4 + "($gp)\n";
        curMipses.append(sw);
        s.offset = global_offset++;//将lval的地址存到内存中
        m.symbol_table.symbols.add(s);
    }
    private void saveFormalMem(MidCode m,String reg,StringBuilder curMipses){//为lval记录指向地址
        String temp = alloc_treg();
        String lw1 = "lw " +temp + " " + m.operand1.offset*4 + "($gp)\n";
        String a = "add " + reg + " " + reg + " " + temp + "\n";
        curMipses.append(lw1).append(a);
        Symbol s = new Symbol(m.operand_2,"lval",m.operand1.dimension,m.use_line,1);//为lval的指向地址设置符号
        String sw = " sw " + reg + " " + global_offset*4 + "($gp)\n";
        curMipses.append(sw);
        s.offset = global_offset++;
        m.symbol_table.symbols.add(s);

    }
    private void getMem(MidCode m,String reg,StringBuilder curMipses){
        String a = "add " + reg + " " + reg + " " + m.operand1.offset + "\n";
        String mul = "mul " + reg + " " + reg + " 4\n";
        String mov_gp = "add $gp $gp " + reg + "\n";
        String loc = alloc_treg();
        String lw = "lw " + loc + " 0($gp)\n";
        String back_gp = "sub $gp $gp " + reg + "\n";
        xt2$tx.put(m.res,loc);
        curMipses.append(a).append(mul).append(mov_gp).append(lw).append(back_gp);
    }
    private void getFormalMem(MidCode m,String reg,StringBuilder curMipses){
        String temp = alloc_treg();
        String lw1 = "lw " +temp + " " + m.operand1.offset*4 + "($gp)\n";
        String a = "add " + reg + " " + reg + " " + temp + "\n";
        String mul = "mul " + reg + " " + reg + " 4\n";
        String mov_gp = "add $gp $gp " + reg + "\n";
        String loc = alloc_treg();
        String lw = "lw " + loc + " 0($gp)\n";
        String back_gp = "sub $gp $gp " + reg + "\n";
        xt2$tx.put(m.res,loc);
        curMipses.append(lw1).append(a).append(mul).append(mov_gp).append(lw).append(back_gp);
    }
    private void handleCal(MidCode m,String operation,StringBuilder curMipses){
        //寻找operand1
        String operand1 ="";
        String t_ident = m.operand_1;
        if(xt2$tx.containsKey(m.operand_1)) m.operand_1 = xt2$tx.get(m.operand_1);//获取操作数储存的临时寄存器
        if(t_ident.charAt(t_ident.length()-1)=='t'&&m.symbol_table.lookupSymbol(t_ident,m.use_line)!=null){
            Symbol operand1_clean = m.symbol_table.lookupSymbol(t_ident,m.use_line);
            if(operand1_clean.reg!=null){
                String mov_reg = "move " +alloc_treg() + " " +operand1_clean.reg+"\n";
                curMipses.append(mov_reg);
                operand1 = "$t" + (t_num+9)%10;
                m.operand_1 = operand1;
            }else if(operand1_clean.offset!=-1){
                String lw = "lw " +alloc_treg() + " " + operand1_clean.offset*4 + "($gp)\n";
                curMipses.append(lw);
                operand1 = "$t" +(t_num + 9)%10;
                m.operand_1 = operand1;
            }
        }//做操作数取回操作
        if(m.operand_1.charAt(0)!='$'&&m.operand_1.charAt(m.operand_1.length()-1)!='t'&&m.operand1 == null){//是立即数 改：operand1不能是立即数！！！
            String li = "li " + alloc_treg() + " " + m.operand_1 + "\n";
            curMipses.append(li);
            operand1 = "$t" + ((t_num+9)%10);
        }else if(m.operand1!=null){//是局部变量
            Symbol symbol_operand1 = m.operand1;
            if(symbol_operand1.reg != null){//该局部变量放在寄存器里
                operand1 = symbol_operand1.reg;
            }else if(symbol_operand1.offset != -1){//该局部变量放在内存里
                operand1 = alloc_treg();
                String move_op1 = "lw " + operand1 + " " +symbol_operand1.offset*4 +"($gp)\n";
                curMipses.append(move_op1);
            }else{//该局部变量已知值，没有分配内存
                String li = "li " + alloc_treg() + " " + symbol_operand1.value + "\n";
                curMipses.append(li);
                operand1 = "$t" + ((t_num+9)%10);
            }
        }else{//是表达式
            operand1 = m.operand_1;//在过去计算过程中已经将中间变量存为operand_1
        }

        //寻找operand2
        if(m.operand_2 == null){//单目计算式
            if(m.operation.equals("-")){
                if(operand1.charAt(operand1.length()-1)=='t'&&Character.isDigit(operand1.charAt(0))) operand1 = xt2$tx.get(operand1);
                if(operand1 == null) operand1 = "$t" + (t_num+9)%10;
                if(Character.isDigit(m.operand_1.charAt(0))){
                    operand1 = m.operand_1;
                }
                String reverse = "sub " + alloc_treg() + " $0 " + operand1 + "\n";
                curMipses.append(reverse);
                xt2$tx.put(m.res,"$t" + (t_num+9)%10);
                m.res = "$t" + (t_num+9)%10;
            }else if(m.operation.equals("+")){
                if(operand1.charAt(operand1.length()-1)=='t'&&Character.isDigit(operand1.charAt(0))) operand1 = xt2$tx.get(operand1);
                if(operand1 == null) operand1 = "$t" + (t_num+9)%10;
                String origin = "move " + alloc_treg() +" " + operand1 + "\n";
                curMipses.append(origin);
                xt2$tx.put(m.res,"$t" + (t_num+9)%10);
                m.res = "$t" + (t_num+9)%10;
            }else if(m.operation.equals("!")){
                if(operand1.charAt(operand1.length()-1)=='t'&&Character.isDigit(operand1.charAt(0))) operand1 = xt2$tx.get(operand1);
                if(operand1 == null) operand1 = "$t" + (t_num+9)%10;
                String target = alloc_treg();
                String sigmoid = "sne " + target + " " + operand1 + " 0\n";//等于0则放置0，不等于0则置1
                String reverse = "sle " + target + " " + target + " 0\n";//!
                curMipses.append(sigmoid).append(reverse);
                xt2$tx.put(m.res,target);
                m.res = target;
            }
        }else{//普通四元式
            String operand2 = "";
            if(xt2$tx.containsKey(m.operand_2)) m.operand_2 = xt2$tx.get(m.operand_2);//获取操作数储存的临时寄存器

            if(m.operand2 == null){
                m.operand2 = m.symbol_table.lookupSymbol(m.operand_2,m.use_line);
            }
            if(m.operand_2.charAt(0)!='$'&&m.operand2 == null&&m.operand_2.charAt(m.operand_2.length()-1)!='t'){//是立即数
                operand2 = m.operand_2;
            }else if(m.operand2!=null&&m.operand2.dimension == 2){//是立即数
                operand2 = m.operand_2;
            }else if(m.operand2!=null){//是局部变量 或 xt
                Symbol symbol_operand2 = m.operand2;
                if(symbol_operand2.reg != null){//该局部变量放在寄存器里
                    operand2 = symbol_operand2.reg;
                }else{//该局部变量放在内存里或符号表里
                    if(symbol_operand2.offset != -1){//内存里
                        operand2 = alloc_treg();
                        String move_op2 = "lw " + operand2 + " " +symbol_operand2.offset*4 +"($gp)\n";
                        curMipses.append(move_op2);
                    }else{//符号表
                        operand2 = String.valueOf(symbol_operand2.value);
                    }
                }
            }else if(m.operand_2.charAt(0)=='$'){//是表达式
                operand2 = m.operand_2;//在过去计算过程中已经将中间变量存为operand_2
            }else{//是表达式但是没有分配存储
                operand2 = "$v0";
            }
            //分配结果寄存器
            String target_reg = alloc_treg();
            xt2$tx.put(m.res,target_reg);
            m.res = target_reg;//修改四元式的目的地址
            String operator = this.operator2operation.get(operation);
            String cal = operator + " " + target_reg + " " + operand1 + " " + operand2 + "\n";
            curMipses.append(cal);
        }
    }
}

class DataEntry{
    String str_ident;//str_0
    String str_content;//"hello world\n"
}
class FuncDefEntry{
    String funcName ;
    StringBuilder funcMipses = new StringBuilder();
}