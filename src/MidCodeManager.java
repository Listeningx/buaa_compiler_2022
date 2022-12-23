import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MidCodeManager {
    List<MidCode> midCodes = new ArrayList<>();
    StringBuilder midCodeOutput = new StringBuilder();
    private int cnt = -1;
    private int cnt_string = -1;
    private String newTemorary(){
//        System.out.println(">>>>"+cnt);
        cnt++;
        return  cnt + "t";
    }
    private void releaseTemporary(){
        cnt--;
    }
    private String newConstString(){
        cnt_string++;
        return "str_" + cnt_string;
    }

    Short_circuit short_circuit = new Short_circuit(this.midCodes,this.midCodeOutput);
    public void addNewMidCode(MidCode new_midCode){
        this.midCodes.add(new_midCode);
    }
    public void generateMidCode(Syntactic_analysis sa){
        TreeNode root = sa.root;//代表CompileUnit结点
        scan(root,0);
    }
    public void scan(TreeNode t,int layer){
        //中间代码产生位置
//        int layer = 0;
        layer++;
//        if(t.childNodes.isEmpty()) return;//递归中止

        if(t.type.equals("FuncDef")){
            //关于这个函数的信息存在上一级符号表中，这个函数中使用的变量等信息存在本级符号表中
                Symbol_Table symbol_table = t.symbol_table;//指向本级符号表
                Symbol thisFunc = symbol_table.parent.lookupSymbol(t.content,t.occur_line);//查找当前函数的符号条目
                MidCode midCode = new MidCode("func_def",symbol_table,t.occur_line);
                midCode.operand_1 = t.content;
                midCode.res = thisFunc.type;
                this.midCodes.add(midCode);
                this.midCodeOutput.append("func_def " + midCode.res + " " + midCode.operand_1 + "\n");

                if(t.childNodes.size() > 2){//有形参
                    List<Symbol> params = thisFunc.params;
                    for(Symbol param : params){
                        MidCode midCode_para = new MidCode("para",param,symbol_table,t.occur_line);
                        midCode_para.operand_1 = param.name;
                        this.midCodes.add(midCode_para);
                        this.midCodeOutput.append("para int " + midCode_para.operand_1 + "\n");
                    }
                }
                //void函数没有return语句需要额外加一个return中间代码

//            if(thisFunc.type.equals("void")&&!this.midCodes.get(this.midCodes.size()-1).operation.equals("return")){
//                MidCode ra = new MidCode("return",symbol_table,t.occur_line);
//                this.midCodes.add(ra);
//            }

        }else if(t.type.equals("MainFuncDef")){
            MidCode midCode = new MidCode("main",t.symbol_table,t.occur_line);
            this.midCodes.add(midCode);
            this.midCodeOutput.append("main:\n");
        }else if(t.type.equals("ConstExp")||t.type.equals("ConstDef")){
            t.con = true;
        }else if(t.fnode!=null&&t.fnode.con){
            t.con = true;//继承到常量属性
        }else if(t.type.equals("Cond")||t.type.equals("LOrExp")||t.type.equals("LAndExp")||t.type.equals("EqExp")||t.type.equals("RelExp")){
            t.logic = true;//条件变量标志
            t.subCond = true;//都是cond的后代
        }

        if(t.fnode!=null&&t.fnode.subCond){
            t.subCond = true;//继承subcond属性
        }

        if(t.type.equals("Stmt")&&t.content.equals("while")){
            short_circuit.before_analyse_while_stmt(t);
        }

        if(t.type.equals("Stmt")&&t.content.equals("if")){
            short_circuit.before_analyse_if_stmt(t);
        }

        //分水岭
        for(TreeNode t1 : t.childNodes){
            scan(t1,layer);
        }

        if(t.type.equals("VarDef")){
            //def系列name存变量名，ident存初始化变量的名或临时变量名
            Boolean initialize = false;
            TreeNode initial = null;
            int time = 0;
            Symbol operand1 = t.symbol_table.lookupSymbol(t.name,t.occur_line);

            for(TreeNode t1:t.childNodes){
                //todo:检查是否有初始化
                if(t1.type.equals("InitVal")){
                    initialize = true;
                    initial = t1;
                }else if(t1.type.equals("ConstExp")){//记录数组维度大小到符号表
                    time++;
                    if(time == 1){
                        operand1.fir_dim = t1.value;
                    }else if(time == 2){
                        operand1.sec_dim = t1.value;
                    }
                }
            }
            //获得综合属性
            if(initialize){
                //varDef获initVal的属性
                t.value = initial.value;
                t.ident = initial.ident;
            }
            //变量声明的四元式
            Symbol_Table symbol_table = t.symbol_table;
            if(operand1!=null){
                if(operand1.dimension == 0){
                    if(!initialize){
                        this.midCodes.add(new MidCode("var int",operand1,symbol_table,t.occur_line));//var int i
                        this.midCodeOutput.append("var int "+t.name + "\n");
                    }else{
                        if(t.ident !=null){
                            this.midCodes.add(new MidCode("var int",operand1,t.ident,symbol_table,t.occur_line));// var int i = t3
                            this.midCodeOutput.append("var int "+ t.name + " = " + t.ident +"\n");
                        }else{
                            this.midCodes.add(new MidCode("var int",operand1,String.valueOf(t.value),t.value,symbol_table,t.occur_line));//var int i = 10
                            this.midCodeOutput.append("var int " + t.name + " = " + t.value + "\n");
                        }
                    }
                }else if(operand1.dimension == 1){
                    this.midCodes.add(new MidCode("var int array",operand1,symbol_table,t.occur_line));//var int a[3];
                    this.midCodeOutput.append("var int array[]"+t.name + "\n");
                    if(!initialize){

                    }else{
                        int cnt = 0;
                        for(TreeNode t1:t.childNodes) {
                            if(t1.type.equals("InitVal")){
                                for(TreeNode t2:t1.childNodes){
                                    MidCode midCode = new MidCode("=",symbol_table,t.occur_line);

                                    //这里用result来表示正在填入的是数组中的第几个
                                    midCode.result = cnt;
                                    midCode.operand_1 = t.name + "[" + cnt + "]";cnt++;
                                    midCode.operand1 = operand1;//a[3]

                                    String operand2 = t2.childNodes.get(0).ident;//exp
                                    if(operand2 == null) operand2 = String.valueOf(t2.childNodes.get(0).value);
                                    midCode.operand_2 = operand2;
                                    if(symbol_table.lookupSymbol(operand2,t.occur_line)!=null) {
                                        midCode.operand2 = symbol_table.lookupSymbol(operand2, t.occur_line);
                                    }


                                    this.midCodes.add(midCode);
                                    this.midCodeOutput.append(midCode.operand_1 + " = " + midCode.operand_2 + "\n");
                                }
                            }
                        }
                    }

                }else if(operand1.dimension == 2){
                    this.midCodes.add(new MidCode("var int array",operand1,symbol_table,t.occur_line));//var int a[3][7];
                    this.midCodeOutput.append("var int array[][]"+t.name + "\n");
                    if(!initialize){

                    }else{
                        int cnt = 0;
                        for(TreeNode t1:t.childNodes) {
                            if(t1.type.equals("InitVal")){
                                for(TreeNode t2:t1.childNodes){
                                    for(TreeNode t3:t2.childNodes){
                                        MidCode midCode = new MidCode("=",symbol_table,t.occur_line);

                                        //这里用result来表示正在填入的是数组中的第几个
                                        midCode.result = cnt;
                                        midCode.operand_1 = t.name + "[" + cnt + "]";cnt++;
                                        midCode.operand1 = operand1;//a[3]这里二维数组当一维数组用就行

                                        String operand2 = t3.childNodes.get(0).ident;//exp
                                        if(operand2 == null) operand2 = String.valueOf(t3.childNodes.get(0).value);
                                        midCode.operand_2 = operand2;
                                        if(symbol_table.lookupSymbol(operand2,t.occur_line)!=null) {
                                            midCode.operand2 = symbol_table.lookupSymbol(operand2, t.occur_line);
                                        }

                                        this.midCodes.add(midCode);
                                        this.midCodeOutput.append(midCode.operand_1 + " = " + midCode.operand_2 + "\n");
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }else if(t.type.equals("ConstDef")){
            boolean initialize = true;
            TreeNode initial = null;
            int time = 0;
            Symbol operand1 = t.symbol_table.lookupSymbol(t.name,t.occur_line);
            Symbol_Table symbol_table = t.symbol_table;

//            if(operand1!=null){
//                if(operand1.dimension == 0){//普通常量的声明
//                    if(!initialize){
//                        this.midCodes.add(new MidCode("const int",operand1,symbol_table,t.occur_line));//const int i
//                        this.midCodeOutput.append("const int "+t.name + "\n");
//                    }else{
//                        if(t.ident !=null){
//                            this.midCodes.add(new MidCode("const int",operand1,t.ident,symbol_table,t.occur_line));// const int i = t3
//                            this.midCodeOutput.append("const int "+ t.name + " = " + t.ident +"\n");
//                        }else{
//                            this.midCodes.add(new MidCode("const int",operand1,String.valueOf(t.value),t.value,symbol_table,t.occur_line));//const int i = 10
//                            this.midCodeOutput.append("const int " + t.name + " = " + t.value + "\n");
//                        }
//                    }
//                }else if(operand1.dimension == 1){//一维数组
//                    this.midCodes.add((new MidCode("const int array",operand1,t.ident,symbol_table,t.occur_line)));
//                    this.midCodeOutput.append("const int array[] " + t.name + "\n");
//                }else if(operand1.dimension == 2){//二维数组
//                    this.midCodes.add((new MidCode("const int array",operand1,t.ident,symbol_table,t.occur_line)));
//                    this.midCodeOutput.append("const int array[][] " + t.name + "\n");
//                }
//
//            }
            for(TreeNode t1:t.childNodes){
                //todo:检查是否有初始化
                if(t1.type.equals("ConstInitVal")){
                    initialize = true;
                    initial = t1;
                }else if(t1.type.equals("ConstExp")){//记录数组维度大小
                    time++;
                    if(time == 1){
                        operand1.fir_dim = t1.value;
                    }else if(time == 2){
                        operand1.sec_dim = t1.value;
                    }
                }
            }
            //获得综合属性
            if(initialize){
                //varDef获initVal的属性
                t.value = initial.value;//必须已知
                t.ident = initial.ident;
            }
            //变量声明的四元式
            if(operand1.dimension == 0){
                operand1.value = t.value;
            }else if(operand1.dimension == 1){
                this.midCodes.add((new MidCode("const int array",operand1,t.ident,symbol_table,t.occur_line)));
                this.midCodeOutput.append("const int array[] " + t.name + "\n");

                int cnt = 0;
                for(TreeNode t1:t.childNodes) {
                    if(t1.type.equals("ConstInitVal")){
                        for(TreeNode t2:t1.childNodes){
                            operand1.values.add(t2.value);
                            MidCode midCode = new MidCode("=",symbol_table,t.occur_line);
                            midCode.operand1 = operand1;
                            midCode.symbol_table = symbol_table;
                            midCode.result = cnt;
                            midCode.operand_2 = String.valueOf(t2.value);
                            this.midCodes.add(midCode);
                            cnt++;
                        }
                    }
                }
            }else if(operand1.dimension == 2){
                this.midCodes.add((new MidCode("const int array",operand1,t.ident,symbol_table,t.occur_line)));
                this.midCodeOutput.append("const int array[][] " + t.name + "\n");

                int cnt = 0;
                for(TreeNode t1:t.childNodes) {
                    if(t1.type.equals("ConstInitVal")){
                        for(TreeNode t2:t1.childNodes){
                            for(TreeNode t3:t2.childNodes){
                                operand1.values.add(t3.value);
                                MidCode midCode = new MidCode("=",symbol_table,t.occur_line);
                                midCode.operand1 = operand1;
                                midCode.symbol_table = symbol_table;
                                midCode.result = cnt;
                                midCode.operand_2 = String.valueOf(t3.value);
                                this.midCodes.add(midCode);
                                cnt++;
                            }
                        }
                    }
                }
            }

        }else if(t.type.equals("FuncFParam")){
            //统计形参第二维长度
            Symbol symbol = t.symbol_table.lookupSymbol(t.name,t.occur_line);
            for(TreeNode t1:t.childNodes){
               if(t1.type.equals("ConstExp")){//记录数组维度大小
                   symbol.sec_dim = t1.value;
                }
            }
        }else if(t.type.equals("UnaryExp")){
            //添加综合属性
            if(t.childNodes.size() == 1){
                t.ident = t.childNodes.get(0).ident;
                t.value = t.childNodes.get(0).value;
                if(t.ident!=null&&t.ident.charAt(t.ident.length()-1)!='t'&&!t.ident.equals("[")){
                    t.value = t.symbol_table.lookupSymbol(t.ident,t.occur_line).value;
                }
            }else{
                t.ident = newTemorary();
            }

            //输出四元式
            if(t.childNodes.size() >= 1&&t.childNodes.get(0).type.equals("UnaryOp")){//unaryOp unaryExp
                if(t.con){
                    if(Objects.equals(t.content, "+")){
                        t.value = t.childNodes.get(1).value ;
                    }else if(Objects.equals(t.content, "-")){
                        t.value = -t.childNodes.get(1).value ;
                    }else{//!
                        if(t.childNodes.get(1).value == 0)t.value =  1;
                        else t.value = 0;
                    }
                }else{
                    Symbol_Table symbol_table = t.symbol_table;
                    MidCode midCode = new MidCode(t.content,symbol_table,t.occur_line);

                    String operand1 = t.childNodes.get(1).ident;
                    if(operand1 == null) operand1 = String.valueOf(t.childNodes.get(1).value);

                    midCode.operand_1 = operand1;
                    midCode.res = t.ident;

                    if(symbol_table.lookupSymbol(operand1,t.occur_line)!=null) {
                        midCode.operand1 = symbol_table.lookupSymbol(operand1, t.occur_line);
                    }

                    if(!t.subCond){
                        this.midCodes.add(midCode);
                        this.midCodeOutput.append(midCode.res + " = " + midCode.operation + " " + midCode.operand_1 + "\n");
                    }else{
                        t.midCodes_hidden.add(midCode);
                        t.midCodeOutput_hidden.append(midCode.res + " = " + midCode.operation + " " + midCode.operand_1 + "\n");
                    }
                }


            }else if(t.childNodes.size() == 1&&t.childNodes.get(0).type.equals("PrimaryExp")){
                //???????
                if(t.con){
                    t.value = t.childNodes.get(0).value;
                }
            }else{//函数调用
                Symbol_Table symbol_table = t.symbol_table;
                if(t.childNodes.size() == 1){
                    t.rParams = t.childNodes.get(0).rParams;
                }

                // 在此加入保存变量的中间代码
                if(!t.subCond){
                    MidCode sv = new MidCode("save_variable",symbol_table,t.occur_line);
                    this.midCodes.add(sv);
                }else{
                    MidCode sv = new MidCode("save_variable",symbol_table,t.occur_line);
                    t.midCodes_hidden.add(sv);
                }

                //在此加入ra压栈的中间代码
                if(!t.subCond){
                    MidCode ra = new MidCode("ra",symbol_table,t.occur_line);
                    this.midCodes.add(ra);
                }else{
                    MidCode ra = new MidCode("ra",symbol_table,t.occur_line);
                    t.midCodes_hidden.add(ra);
                }

                //参数压栈
                for(int it = t.rParams.size() - 1;it >=0;it--){
                    RParam rParam = t.rParams.get(it);

//                for(RParam rParam : t.rParams){
                    if(rParam.ident!=null){
                        MidCode midCode = new MidCode("push",symbol_table,t.occur_line);
                        midCode.operand_1 = rParam.ident;
                        if(!t.subCond){
                            this.midCodes.add(midCode);
                            this.midCodeOutput.append("push " + midCode.operand_1 + "\n");
                        }else{
                            t.midCodes_hidden.add(midCode);
                            t.midCodeOutput_hidden.append("push " + midCode.operand_1 + "\n");
                        }

                    }else{
                        //找一个临时变量保存数值
                        String operand_1 = newTemorary();
                        String operand_2 = String.valueOf(rParam.value);
                        MidCode assignMidCode = new MidCode("=",symbol_table,t.occur_line);
                        assignMidCode.operand_1 = operand_1;
                        assignMidCode.operand_2 = operand_2;
                        if(!t.subCond){
                            this.midCodes.add(assignMidCode);
                            this.midCodeOutput.append(assignMidCode.operand_1 + " = " +assignMidCode.operand_2 + "\n" );
                        }else{
                            t.midCodes_hidden.add(assignMidCode);
                            t.midCodeOutput_hidden.append(assignMidCode.operand_1 + " = " +assignMidCode.operand_2 + "\n" );
                        }

                        //把临时变量压栈
                        MidCode midCode = new MidCode("push",symbol_table,t.occur_line);
                        midCode.operand_1 = operand_1;
                        if(!t.subCond){
                            this.midCodes.add(midCode);
                            this.midCodeOutput.append("push " + midCode.operand_1 + "\n");
                        }else{
                            t.midCodes_hidden.add(midCode);
                            t.midCodeOutput_hidden.append("push " + midCode.operand_1 + "\n");
                        }

                        releaseTemporary();
                    }
                }

                //跳转到函数定义部分
                MidCode midCode = new MidCode("call",symbol_table,t.occur_line);
                Symbol objectFunc = symbol_table.lookupSymbol(t.content,t.occur_line);
                midCode.operand1 = objectFunc;
                midCode.res = t.content;
                if(!t.subCond){
                    this.midCodes.add(midCode);
                    this.midCodeOutput.append("call ").append(midCode.res).append("\n");
                }else{
                    t.midCodes_hidden.add(midCode);
                    t.midCodeOutput_hidden.append("call ").append(midCode.res).append("\n");
                }


                //保存返回值，如果有的话
                if(objectFunc.type!=null&&objectFunc.type.equals("int")){
                    String operand_1 = newTemorary();
                    String operand_2 = "RET";
                    MidCode retMidCode = new MidCode("==",symbol_table,t.occur_line);
                    retMidCode.operand_1 = operand_1;
                    retMidCode.operand_2 = operand_2;
                    if(!t.subCond){
                        this.midCodes.add(retMidCode);
                        this.midCodeOutput.append(operand_1 + " = " + operand_2 + "\n");
                    }else{
                        t.midCodes_hidden.add(retMidCode);
                        t.midCodeOutput_hidden.append(operand_1 + " = " + operand_2 + "\n");
                    }


                    t.ident = operand_1;
                }

            }
        }else if(t.type.equals("MulExp")){
            //获得综合属性
            if(t.childNodes.size() == 1){//必定是unaryExp
                t.ident = t.childNodes.get(0).ident;
                t.value = t.childNodes.get(0).value;
            }else{//mulExp */% addExp
                if(t.con){
                    if(Objects.equals(t.content, "*")){
                        t.value = t.childNodes.get(0).value * t.childNodes.get(1).value;
                    }else if(Objects.equals(t.content, "/")){
                        t.value = t.childNodes.get(0).value / t.childNodes.get(1).value;
                    }else{//%
                        t.value = t.childNodes.get(0).value % t.childNodes.get(1).value;
                    }
                }else{
                    t.ident = newTemorary();//获取新的临时变量来存放中间结果
                }
            }



            //保存输出四元式
            if(t.childNodes.size() > 1&&!t.con){//只有>1的情况才需要四元式
                Symbol_Table symbol_table = t.symbol_table;
                MidCode midCode = new MidCode(t.content,symbol_table,t.occur_line);

                String operand1 = t.childNodes.get(0).ident;
                if(operand1 == null) operand1 = String.valueOf(t.childNodes.get(0).value);

                String operand2 = t.childNodes.get(1).ident;
                if(operand2 == null) operand2 = String.valueOf(t.childNodes.get(1).value);

                midCode.operand_1 = operand1;
                midCode.operand_2 = operand2;
                midCode.res = t.ident;

                if(symbol_table.lookupSymbol(operand1,t.occur_line)!=null) {
                    midCode.operand1 = symbol_table.lookupSymbol(operand1, t.occur_line);
                }
                if(symbol_table.lookupSymbol(operand2,t.occur_line)!=null){
                    midCode.operand2 = symbol_table.lookupSymbol(operand2,t.occur_line);
                }

                if(!t.subCond){
                    this.midCodes.add(midCode);
                    this.midCodeOutput.append(midCode.res + " = " + midCode.operand_1 + " " + midCode.operation + " " + midCode.operand_2 + "\n");
                }else{
                    t.midCodes_hidden.add(midCode);
                    t.midCodeOutput_hidden.append(midCode.res + " = " + midCode.operand_1 + " " + midCode.operation + " " + midCode.operand_2 + "\n");
                }

            }

            if(t.ident!=null&&(t.ident.charAt(t.ident.length()-1))=='t'){
                if(t.symbol_table.lookupSymbol(t.ident,-9)==null){
                    //做一次长期保存操作
                    MidCode midCode_savet = new MidCode("=",t.symbol_table,t.occur_line);
                    midCode_savet.operand_2 = t.ident;

                    if(!t.subCond){
                        this.midCodes.add(midCode_savet);
                        this.midCodeOutput.append("save " + midCode_savet.operand_2+"\n");
                    }else{
                        t.midCodes_hidden.add(midCode_savet);
                        t.midCodeOutput_hidden.append("save " + midCode_savet.operand_2+"\n");
                    }
                }
            }
        }else if(t.type.equals("AddExp")){
            //获得综合属性
            if(t.childNodes.size() == 1){//必定是mulExp
                t.ident = t.childNodes.get(0).ident;
                t.value = t.childNodes.get(0).value;
            }else{//addExp +/- mulExp
                if(t.con){
                    if(Objects.equals(t.content, "+")){
                        t.value = t.childNodes.get(0).value + t.childNodes.get(1).value;
                    }else{// -
                        t.value = t.childNodes.get(0).value - t.childNodes.get(1).value;
                    }
                }else{
                    t.ident = newTemorary();//获取新的临时变量来存放中间结果
                }
            }

            //保存输出四元式
            if(t.childNodes.size() > 1&& !t.con){//只有>1的情况才需要四元式
                Symbol_Table symbol_table = t.symbol_table;
                MidCode midCode = new MidCode(t.content,symbol_table,t.occur_line);

                String operand1 = t.childNodes.get(0).ident;
                if(operand1 == null) operand1 = String.valueOf(t.childNodes.get(0).value);

                String operand2 = t.childNodes.get(1).ident;
                if(operand2 == null) operand2 = String.valueOf(t.childNodes.get(1).value);

                midCode.operand_1 = operand1;
                midCode.operand_2 = operand2;
                midCode.res = t.ident;

                if(symbol_table.lookupSymbol(operand1,t.occur_line)!=null) {
                    midCode.operand1 = symbol_table.lookupSymbol(operand1, t.occur_line);
                }
                if(symbol_table.lookupSymbol(operand2,t.occur_line)!=null){
                    midCode.operand2 = symbol_table.lookupSymbol(operand2,t.occur_line);
                }

                if(!t.subCond){
                    this.midCodes.add(midCode);
                    this.midCodeOutput.append(midCode.res + " = " + midCode.operand_1 + " " + midCode.operation + " " + midCode.operand_2 + "\n");
                }else{
                    t.midCodes_hidden.add(midCode);
                    t.midCodeOutput_hidden.append(midCode.res + " = " + midCode.operand_1 + " " + midCode.operation + " " + midCode.operand_2 + "\n");
                }

            }

                if(t.ident!=null&&(t.ident.charAt(t.ident.length()-1))=='t'){
                    if(t.symbol_table.lookupSymbol(t.ident,-9)==null){
                        //做一次长期保存操作
                        MidCode midCode_savet = new MidCode("=",t.symbol_table,t.occur_line);
                        midCode_savet.operand_2 = t.ident;
                        if(!t.subCond){
                            this.midCodes.add(midCode_savet);
                            this.midCodeOutput.append("save " + midCode_savet.operand_2+"\n");
                        }else{
                            t.midCodes_hidden.add(midCode_savet);
                            t.midCodeOutput_hidden.append("save " + midCode_savet.operand_2+"\n");
                        }
                    }
            }
        }else if(t.type.equals("Exp")){
            //添加综合属性
            t.ident = t.childNodes.get(0).ident;
            t.value = t.childNodes.get(0).value;
        }else if(t.type.equals("ConstExp")){
            t.ident = t.childNodes.get(0).ident;
            t.value = t.childNodes.get(0).value;

        }else if(t.type.equals("PrimaryExp")){
            t.ident = t.childNodes.get(0).ident;
            t.value = t.childNodes.get(0).value;
        }else if(t.type.equals("Stmt")){
            //保存输出赋值语句的中间代码
            if(t.content.equals("assign")){//赋值语句

                Symbol_Table symbol_table = t.symbol_table;
                MidCode midCode = new MidCode("=",symbol_table,t.occur_line);

                String operand1 = t.childNodes.get(0).ident;//lval
                midCode.operand_1 = operand1;
                if(symbol_table.lookupSymbol(operand1,t.occur_line)!=null) {
                    midCode.operand1 = symbol_table.lookupSymbol(operand1, t.occur_line);
                }

                String operand2 = t.childNodes.get(1).ident;//exp
                if(operand2 == null) operand2 = String.valueOf(t.childNodes.get(1).value);
                midCode.operand_2 = operand2;
                if(symbol_table.lookupSymbol(operand2,t.occur_line)!=null) {
                    midCode.operand2 = symbol_table.lookupSymbol(operand2, t.occur_line);
                }

                this.midCodes.add(midCode);
                this.midCodeOutput.append(midCode.operand_1 + " = " + midCode.operand_2 + "\n");
            }else if(t.content.equals("read")){
                Symbol_Table symbol_table = t.symbol_table;
                MidCode midCode = new MidCode("scanf",symbol_table,t.occur_line);

                String operand1 = t.childNodes.get(0).ident;//lval
                midCode.operand_1 = operand1;
                if(symbol_table.lookupSymbol(operand1,t.occur_line)!=null) {
                    midCode.operand1 = symbol_table.lookupSymbol(operand1, t.occur_line);
                }
                midCode.operand_2 = newTemorary();
//                releaseTemporary();

                this.midCodes.add(midCode);
                this.midCodeOutput.append("scanf " + midCode.operand_2 + "\n" + midCode.operand_1 + " = " + midCode.operand_2 + "\n");
            }else if(!t.content.equals("") &&t.content.charAt(0)=='\"'){//输出语句
                String str = t.content;
                int it = 1;
                StringBuilder curStr = new StringBuilder("\"");

                int exp_pointer = 0;
                while(it < str.length()-1){
                    if(str.charAt(it)=='%'){
                        curStr.append("\"");

                        //保存字符串常量并输出
                        MidCode midCode_str = new MidCode("printf const str",t.symbol_table,t.occur_line);
                        midCode_str.res = curStr.toString();
                        midCode_str.operand_1 = newConstString();
                        this.midCodes.add(midCode_str);
                        this.midCodeOutput.append("const str "+midCode_str.operand_1 + " = " + midCode_str.res + "\n");
                        this.midCodeOutput.append("printf "+ midCode_str.operand_1 + "\n");


                        //输出%d表达式
                        MidCode midCode_exp = new MidCode("printf %d",t.symbol_table,t.occur_line);
                        midCode_exp.operand_1 = t.childNodes.get(exp_pointer).ident;
                        if(midCode_exp.operand_1 == null){
                            midCode_exp.operand_1 = String.valueOf(t.childNodes.get(exp_pointer).value);
                        }exp_pointer++;
                        this.midCodes.add(midCode_exp);
                        this.midCodeOutput.append("printf "+ midCode_exp.operand_1 + "\n");

                        //更新当前字符串和字符指针
                        curStr = new StringBuilder("\"");
                        it +=2 ;
                    }
                    if(it >= str.length()) break;
                    curStr.append(str.charAt(it));
                    it++;
                }
                if(it == str.length()-1){
                    curStr.append("\"");

                    MidCode midCode_str = new MidCode("printf const str",t.symbol_table,t.occur_line);
                    midCode_str.res = curStr.toString();
                    midCode_str.operand_1 = newConstString();
                    this.midCodes.add(midCode_str);
                    this.midCodeOutput.append("const str "+midCode_str.operand_1 + " = " + midCode_str.res + "\n");
                    this.midCodeOutput.append("printf "+ midCode_str.operand_1 + "\n");
                }
            }else if(t.content.equals("return")){//返回语句
                Symbol_Table symbol_table = t.symbol_table;
                MidCode midCode = new MidCode("return",t.symbol_table,t.occur_line);
                if(t.childNodes.size() > 0){//有返回值
                    // midCode.res = "int";//暂时没想到有什么用
                    if(t.childNodes.get(0).ident ==null){//常数值
                        midCode.result = t.childNodes.get(0).value;
                        midCode.operand_1 = String.valueOf(t.childNodes.get(0).value);
                    }else{//表达式中间结果/变量
                        midCode.res = t.childNodes.get(0).ident;
                        midCode.operand_1 = t.childNodes.get(0).ident;
                        if(symbol_table.lookupSymbol(midCode.operand_1,t.occur_line)!=null){
                            midCode.operand1 = symbol_table.lookupSymbol(midCode.operand_1,t.occur_line);
                        }
                    }
                }
                this.midCodes.add(midCode);
            } //输出与否意义不大
            else if(t.content.equals("break")){
                TreeNode tmp = t;
                while(!tmp.content.equals("while")) tmp = tmp.fnode;
                MidCode m = new MidCode("jump_without_condition",null,tmp.label_end,t.symbol_table,t.occur_line);
                midCodes.add(m);
                midCodeOutput.append("j " + m.res + "\n");
            }else if(t.content.equals("continue")){
                TreeNode tmp = t;
                while(!tmp.content.equals("while")) tmp = tmp.fnode;
                MidCode m = new MidCode("jump_without_condition",null,tmp.label_begin,t.symbol_table,t.occur_line);
                midCodes.add(m);
                midCodeOutput.append("j " + m.res + "\n");
            }
        }else if(t.type.equals("ConstInitVal")){
            t.ident = t.childNodes.get(0).ident;
            t.value = t.childNodes.get(0).value;
        }else if(t.type.equals("InitVal")){
            t.ident = t.childNodes.get(0).ident;
            t.value = t.childNodes.get(0).value;
        }else if(t.type.equals("FuncRParams")){
            //获得综合属性：所有实参
            for(TreeNode t_exp:t.childNodes){
                RParam rParam = new RParam();
                if(t_exp.ident != null){
                    rParam.ident = t_exp.ident;
                }else {
                    rParam.value = t_exp.value;
                }
                t.rParams.add(rParam);
            }
        }else if(t.logic&&t.childNodes.size() > 1){//condexp的构建
            t.condExp.left = t.childNodes.get(0);
            t.condExp.right = t.childNodes.get(1);
            t.condExp.op = t.content;
            t.ident = newTemorary();


            //保存输出四元式

                Symbol_Table symbol_table = t.symbol_table;
                MidCode midCode = new MidCode(t.content + " cond",symbol_table,t.occur_line);

                String operand1 = t.childNodes.get(0).ident;
                if(operand1 == null) operand1 = String.valueOf(t.childNodes.get(0).value);

                String operand2 = t.childNodes.get(1).ident;
                if(operand2 == null) operand2 = String.valueOf(t.childNodes.get(1).value);

                midCode.operand_1 = operand1;
                midCode.operand_2 = operand2;
                midCode.res = t.ident;

                if(symbol_table.lookupSymbol(operand1,t.occur_line)!=null) {
                    midCode.operand1 = symbol_table.lookupSymbol(operand1, t.occur_line);
                }
                if(symbol_table.lookupSymbol(operand2,t.occur_line)!=null){
                    midCode.operand2 = symbol_table.lookupSymbol(operand2,t.occur_line);
                }


                    assert t.subCond;
                    t.midCodes_hidden.add(midCode);
                    t.midCodeOutput_hidden.append(midCode.res + " = " + midCode.operand_1 + " " + t.content + " " + midCode.operand_2 + "\n");




            if(t.ident!=null&&(t.ident.charAt(t.ident.length()-1))=='t'){
                if(t.symbol_table.lookupSymbol(t.ident,-9)==null){
                    //做一次长期保存操作
                    MidCode midCode_savet = new MidCode("=",t.symbol_table,t.occur_line);
                    midCode_savet.operand_2 = t.ident;
                    if(!t.subCond){
                        this.midCodes.add(midCode_savet);
                        this.midCodeOutput.append("save " + midCode_savet.operand_2+"\n");
                    }else{
                        t.midCodes_hidden.add(midCode_savet);
                        t.midCodeOutput_hidden.append("save " + midCode_savet.operand_2+"\n");
                    }
                }

            }
        }else if(t.logic&&t.childNodes.size() == 1&&!t.type.equals("RelExp")){//condexp的构建,直接传入类
            t.condExp = t.childNodes.get(0).condExp;

            t.ident = t.childNodes.get(0).ident;
            t.value = t.childNodes.get(0).value;
        }else if(t.logic&&t.childNodes.size() == 1) {//逻辑和算术的分界节点
            t.condExp.left = t.childNodes.get(0);
            t.condExp.op = "bottom";

                t.ident = t.childNodes.get(0).ident;
                t.value = t.childNodes.get(0).value;

        }else if(t.logic&&t.type.equals("RelExp")){
            if(t.childNodes.size() > 1){
                MidCode m = new MidCode(t.content,t.symbol_table,t.occur_line);
                m.operand_1 = t.childNodes.get(0).ident;
                m.operand_2 = t.childNodes.get(1).ident;
            }
        }else if(t.type.equals("LVal")) {
                if(t.childNodes.size() == 0 && t.con){
                    Symbol symbol = t.symbol_table.lookupSymbol(t.ident, t.occur_line);
                    t.value = symbol.value;
                }else if(t.childNodes.size() == 0 && !t.con){
                    Symbol symbol = t.symbol_table.lookupSymbol(t.ident, t.occur_line);

                } else if (t.childNodes.size() > 0 && t.con) {
                    Symbol symbol = t.symbol_table.lookupSymbol(t.ident, t.occur_line);
                    if (symbol.dimension == 1) {
                        int offset = t.childNodes.get(0).value;
                        t.value = symbol.values.get(offset);
                    } else if (symbol.dimension == 2) {
                        int offset = t.childNodes.get(0).value * symbol.sec_dim + t.childNodes.get(1).value;
                        t.value = symbol.values.get(offset);
                    }
                } else if (t.childNodes.size() > 0 && t.fnode != null && !t.fnode.type.equals("Stmt")) {//取值运算
                    Symbol symbol = t.symbol_table.lookupSymbol(t.ident, t.occur_line);

                    if (symbol.dimension == 1) {//a[1]
                        t.ident = newTemorary();
                        if (t.childNodes.get(0).ident == null) {
                            int offset = t.childNodes.get(0).value;
                            MidCode midCode = new MidCode("[value]", symbol, t.ident, offset, t.symbol_table, t.occur_line);
                            if(!t.subCond){
                                midCodes.add(midCode);
                                midCodeOutput.append(t.ident + " = " + symbol.name + "[" + offset + "]\n");
                            }else{
                                t.midCodes_hidden.add(midCode);
                                t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + "[" + offset + "]\n");
                            }

                        } else {//a[3+c]
                            Symbol s = t.symbol_table.lookupSymbol(t.childNodes.get(0).ident, t.occur_line);
                            if (s == null) {
                                MidCode midCode = new MidCode("[xt]", symbol, t.childNodes.get(0).ident, t.symbol_table, t.occur_line);
                                midCode.operand_2 = t.ident;
                                if(!t.subCond){
                                    midCodes.add(midCode);
                                    midCodeOutput.append(t.ident + " = " + symbol.name + "[" + t.childNodes.get(0).ident + "]\n");
                                }else{
                                    t.midCodes_hidden.add(midCode);
                                    t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + "[" + t.childNodes.get(0).ident + "]\n");
                                }

                            } else {//a[c]
                                MidCode midCode = new MidCode("[var]", symbol, s, 0, t.symbol_table);
                                midCode.operand_2 = t.ident;
                                if(!t.subCond){
                                    midCodes.add(midCode);
                                    midCodeOutput.append(t.ident + " = " + symbol.name + "[" + s.name + "]\n");
                                }else{
                                    t.midCodes_hidden.add(midCode);
                                    t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + "[" + s.name + "]\n");
                                }

                            }
                        }
                    } else if (symbol.dimension == 2) {
                        t.ident = newTemorary();

                        if (t.childNodes.size() == 2) {
                            MidCode m = new MidCode("[][]", symbol, t.symbol_table, t.occur_line);//二维数组下面跟两条中间代码
                            if(!t.subCond){
                                this.midCodes.add(m);
                                this.midCodeOutput.append(symbol.name + "[][]\n");
                            }else{
                                t.midCodes_hidden.add(m);
                                t.midCodeOutput_hidden.append(symbol.name + "[][]\n");
                            }

                            for (TreeNode t1 : t.childNodes) {
                                if (t1.ident == null) {
                                    int offset = t1.value;
                                    MidCode midCode = new MidCode("[value]", symbol, t.ident, offset, t.symbol_table, t.occur_line);
                                    if(!t.subCond){
                                        midCodes.add(midCode);
                                        midCodeOutput.append(t.ident + " = " + symbol.name + "[" + offset + "]\n");
                                    }else{
                                        t.midCodes_hidden.add(midCode);
                                        t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + "[" + offset + "]\n");
                                    }

                                } else {
                                    Symbol s = t.symbol_table.lookupSymbol(t1.ident, t.occur_line);
                                    if (s == null) {
                                        MidCode midCode = new MidCode("[xt]", symbol, t1.ident, t.symbol_table, t.occur_line);
                                        midCode.operand_2 = t.ident;
                                        if(!t.subCond){
                                            midCodes.add(midCode);
                                            midCodeOutput.append(t.ident + " = " + symbol.name + "[" + t1.ident + "]\n");
                                        }else{
                                            t.midCodes_hidden.add(midCode);
                                            t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + "[" + t1.ident + "]\n");
                                        }

                                    } else {
                                        MidCode midCode = new MidCode("[var]", symbol, s, 0, t.symbol_table);
                                        midCode.operand_2 = t.ident;
                                        if(!t.subCond){
                                            midCodes.add(midCode);
                                            midCodeOutput.append(t.ident + " = " + symbol.name + "[" + s.name + "]\n");
                                        }else{
                                            t.midCodes_hidden.add(midCode);
                                            t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + "[" + s.name + "]\n");
                                        }

                                    }
                                }
                            }
                        } else {//部分传参
                            TreeNode t1 = t.childNodes.get(0);
                            if (t1.ident == null) {
                                Symbol_Table symbol_table = t.symbol_table;
                                int offset = t1.value * symbol.sec_dim;
                                MidCode midCode = new MidCode("++", symbol_table, t.occur_line);

                                midCode.operand_1 = String.valueOf(offset);
                                midCode.operand_2 = String.valueOf(symbol.offset);
                                midCode.operand2 = symbol;
                                midCode.res = t.ident;

                                if(!t.subCond){
                                    midCodes.add(midCode);
                                    midCodeOutput.append(t.ident + " = " + symbol.name + " + " + offset + "\n");
                                }else{
                                    t.midCodes_hidden.add(midCode);
                                    t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + " + " + offset + "\n");
                                }

                            } else {
                                Symbol s = t.symbol_table.lookupSymbol(t1.ident, t.occur_line);
                                if (s == null) {
                                    Symbol_Table symbol_table = t.symbol_table;
                                    MidCode ass_midCode = new MidCode("*", symbol_table, t.occur_line);

                                    String operand1 = t1.ident;

                                    String operand2 = String.valueOf(symbol.sec_dim);

                                    ass_midCode.operand_1 = operand1;
                                    ass_midCode.operand_2 = operand2;
                                    String temp = newTemorary();
                                    ass_midCode.res = temp;



                                    MidCode midCode = new MidCode("++", symbol_table, t.occur_line);

                                    midCode.operand_1 = ass_midCode.res;
                                    midCode.operand_2 = String.valueOf(symbol.offset);
                                    midCode.operand2 = symbol;
                                    midCode.res = t.ident;
                                    if(!t.subCond){
                                        this.midCodes.add(ass_midCode);
                                        this.midCodeOutput.append(ass_midCode.res + " = " + ass_midCode.operand_1 + " " + ass_midCode.operation + " " + ass_midCode.operand_2 + "\n");

                                        midCodes.add(midCode);
                                        midCodeOutput.append(t.ident + " = " + symbol.name + " + " + ass_midCode.res + "\n");
                                    }else{
                                        t.midCodes_hidden.add(ass_midCode);
                                        t.midCodes_hidden.add(midCode);

                                        t.midCodeOutput_hidden.append(ass_midCode.res + " = " + ass_midCode.operand_1 + " " + ass_midCode.operation + " " + ass_midCode.operand_2 + "\n");
                                        t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + " + " + ass_midCode.res + "\n");
                                    }


                                } else {
                                    Symbol_Table symbol_table = t.symbol_table;
                                    MidCode midCode = new MidCode("*", symbol_table, t.occur_line);

                                    midCode.operand_1 = t1.ident;
                                    midCode.operand_2 = String.valueOf(symbol.sec_dim);
                                    String temp = newTemorary();
                                    midCode.res = temp;

                                    midCode.operand1 = s;

                                    MidCode midCode_2 = new MidCode("++", symbol_table, t.occur_line);

                                    midCode_2.operand_1 = midCode.res;
                                    midCode_2.operand_2 = String.valueOf(symbol.offset);
                                    midCode_2.operand2 = symbol;
                                    midCode_2.res = t.ident;


                                    if(!t.subCond){
                                        this.midCodes.add(midCode);
                                        this.midCodeOutput.append(midCode.res + " = " + midCode.operand_1 + " " + midCode.operation + " " + midCode.operand_2 + "\n");

                                        midCodes.add(midCode_2);
                                        midCodeOutput.append(t.ident + " = " + symbol.name + " + " + midCode.res + "\n");
                                    }else{
                                        t.midCodes_hidden.add(midCode);
                                        t.midCodes_hidden.add(midCode_2);

                                        t.midCodeOutput_hidden.append(midCode.res + " = " + midCode.operand_1 + " " + midCode.operation + " " + midCode.operand_2 + "\n");
                                        t.midCodeOutput_hidden.append(t.ident + " = " + symbol.name + " + " + midCode.res + "\n");
                                    }
                                }

                            }
                        }

                    }
                } else if (t.childNodes.size() > 0 && t.fnode != null && t.fnode.type.equals("Stmt")) {//数组存值
                    Symbol symbol = t.symbol_table.lookupSymbol(t.ident, t.occur_line);

                    if (symbol.dimension == 1) {//a[1]
                        t.ident = newTemorary();
                        if (t.childNodes.get(0).ident == null) {
                            int offset = t.childNodes.get(0).value;
                            MidCode midCode = new MidCode("save [value]", symbol, t.ident, offset, t.symbol_table, t.occur_line);
                            midCode.operand_2 = t.ident;
                            midCodes.add(midCode);
                            midCodeOutput.append("save " + t.ident + " = " + symbol.name + "[" + offset + "]\n");
                        } else {//a[3+c]
                            Symbol s = t.symbol_table.lookupSymbol(t.childNodes.get(0).ident, t.occur_line);
                            if (s == null) {
                                MidCode midCode = new MidCode("save [xt]", symbol, t.childNodes.get(0).ident, t.symbol_table, t.occur_line);
                                midCode.operand_2 = t.ident;
                                midCodes.add(midCode);
                                midCodeOutput.append("save " + t.ident + " = " + symbol.name + "[" + t.childNodes.get(0).ident + "]\n");
                            } else {//a[c]
                                MidCode midCode = new MidCode("save [var]", symbol, s, 0, t.symbol_table);
                                midCode.operand_2 = t.ident;
                                midCodes.add(midCode);
                                midCodeOutput.append("save " + t.ident + " = " + symbol.name + "[" + s.name + "]\n");
                            }
                        }
                    } else if (symbol.dimension == 2) {
                        t.ident = newTemorary();
                        MidCode m = new MidCode("save [][]", symbol, t.symbol_table, t.occur_line);//二维数组下面跟两条中间代码
                        this.midCodes.add(m);
                        this.midCodeOutput.append("save" + symbol.name + "[][]\n");
                        if (t.childNodes.size() == 2) {
                            for (TreeNode t1 : t.childNodes) {
                                if (t1.ident == null) {
                                    int offset = t1.value;
                                    MidCode midCode = new MidCode("save [value]", symbol, t.ident, offset, t.symbol_table, t.occur_line);
                                    midCode.operand_2 = t.ident;
                                    midCodes.add(midCode);
                                    midCodeOutput.append("save " + t.ident + " = " + symbol.name + "[" + offset + "]\n");
                                } else {
                                    Symbol s = t.symbol_table.lookupSymbol(t1.ident, t.occur_line);
                                    if (s == null) {
                                        MidCode midCode = new MidCode("save [xt]", symbol, t1.ident, t.symbol_table, t.occur_line);
                                        midCode.operand_2 = t.ident;
                                        midCodes.add(midCode);
                                        midCodeOutput.append("save " + t.ident + " = " + symbol.name + "[" + t1.ident + "]\n");
                                    } else {
                                        MidCode midCode = new MidCode("save [var]", symbol, s, 0, t.symbol_table);
                                        midCode.operand_2 = t.ident;
                                        midCodes.add(midCode);
                                        midCodeOutput.append("save " + t.ident + " = " + symbol.name + "[" + s.name + "]\n");
                                    }
                                }
                            }
                        }

                    }
                }



            }

                if(t.type.equals("Cond")&&t.fnode.content.equals("while")){
                    t.condExp.jumpWhenFalse(t.fnode.label_end,midCodes,midCodeOutput,t.symbol_table,t.occur_line,0);
                }

                if(t.type.equals("Cond")&&t.fnode.content.equals("if")){
                    t.condExp.jumpWhenFalse(t.fnode.label_begin, midCodes,midCodeOutput,t.symbol_table,t.occur_line,0);
                }

                if(t.type.equals("Stmt")&&t.fnode.content.equals("while")){
                    //跳转到whileBegin
                    MidCode m = new MidCode("jump_without_condition",null,t.fnode.label_begin, t.symbol_table,t.occur_line);//res = beginlabel
                    midCodes.add(m);
                    midCodeOutput.append("j " + m.res + "\n");

                    //加入whileEnd标签
                    MidCode midCode = new MidCode("label",null,t.fnode.label_end, t.symbol_table,t.occur_line);//res = endLabel
                    this.midCodes.add(midCode);
                    this.midCodeOutput.append(midCode.res + ":\n");
                }

                if(t.type.equals("Stmt")&&t.fnode.content.equals("if")&&t.fnode.childNodes.size()>2&&t==t.fnode.childNodes.get(1)){//有else 的情况(中间部分的标签)
                    //跳转到else_end
                    MidCode m = new MidCode("jump_without_condition",null,t.fnode.label_end, t.symbol_table,t.occur_line);//res = else_end
                    midCodes.add(m);
                    midCodeOutput.append("j " + m.res + "\n");

                    //加入ifEnd标签
                    MidCode midCode = new MidCode("label",null,t.fnode.label_begin, t.symbol_table,t.occur_line);//res = if_end
                    this.midCodes.add(midCode);
                    this.midCodeOutput.append(midCode.res + ":\n");
                }

                if(t.type.equals("Stmt")&&t.content.equals("if")&&t.childNodes.size()>2){//有else 的情况(结束部分的标签)
                    //加入elseEnd标签
                    MidCode midCode = new MidCode("label",null,t.label_end, t.symbol_table,t.occur_line);//res = else_end
                    this.midCodes.add(midCode);
                    this.midCodeOutput.append(midCode.res + ":\n");
                }

                if(t.type.equals("Stmt")&&t.fnode.content.equals("if")&&t.fnode.childNodes.size()==2){//无else 的情况
                    //加入ifEnd标签
                    MidCode midCode = new MidCode("label",null,t.fnode.label_begin, t.symbol_table,t.occur_line);//res = if_end
                    this.midCodes.add(midCode);
                    this.midCodeOutput.append(midCode.res + ":\n");
                }


            }

    public MidCodeManager(){
    }
    public void addUseLine(MidCode m,int useLine){
        m.use_line = useLine;
    }
}

class MidCode{
    String operation;
    Symbol operand1;
    String operand_1;//当操作数是t0,t1..时
    Symbol operand2;
    String operand_2;
    String res;
    int result;
    int use_line = -1;
    Symbol_Table symbol_table;
    public MidCode(String operation,Symbol operand1,Symbol operand2,int result,Symbol_Table symbol_table){
        this.operand1 = operand1;
        this.operand2 = operand2;
        this.operation = operation;
        this.result = result;
        this.symbol_table = symbol_table;
    }
    public MidCode(String operation,Symbol operand1,String res,int result,Symbol_Table symbol_table,int useLine){
        this.res = res;
        this.operation = operation;
        this.operand1 = operand1;
        this.result = result;
        this.symbol_table = symbol_table;
        this.use_line = useLine;
    }
    public MidCode(String operation,Symbol operand1,String res,Symbol_Table symbol_table,int useLine){
        this.res = res;
        this.operation = operation;
        this.operand1 = operand1;
        this.symbol_table = symbol_table;
        this.use_line = useLine;
    }
    public MidCode(String operation,Symbol operand1,Symbol_Table symbol_table,int use_line){
        this.operation = operation;
        this.operand1 = operand1;
        this.symbol_table = symbol_table;
        this.use_line = use_line;
    }
    public MidCode(Symbol operand1,Symbol_Table symbol_table,int use_line){
        this.operand1 = operand1;
        this.symbol_table = symbol_table;
        this.use_line = use_line;
    }
    public MidCode(String operation,Symbol_Table symbol_table,int use_line){
        this.operation = operation;
        this.symbol_table = symbol_table;
        this.use_line = use_line;
    }
    public void outputMidCode(){
        //输出四元式中间代码
    }
}