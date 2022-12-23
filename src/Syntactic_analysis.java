import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Syntactic_analysis {
    StringBuilder ans = new StringBuilder();
    int i = 0;
    int sum = 0;
    int cycleDepth = 0;//记录循环层数，错误处理用到
    StringBuilder errors = new StringBuilder();
    Error sa_errors = new Error();
    Symbol_Table curSymbolTable = new Symbol_Table();
    Map<String,Symbol_Table> name2symboltable = new HashMap<>();
    ArrayList<Word> words = new ArrayList<>();
    TreeNode root = new TreeNode();
//    Identify_words iw = new Identify_words();
    public Syntactic_analysis(ArrayList<Word> words){
        this.words = words;
        sum = words.size();
        root.type = "CompUnit";
        Symbol_Table root_table = new Symbol_Table(null,"out");//初始化根符号表，父节点是null
        curSymbolTable = root_table;//设置当前符号表指向根符号表
        this.name2symboltable.put("out",root_table);

    }
    public TreeNode begin_analysis() throws IOException {
        //CompUnit的分析
        while(isDecl()&&!ReadWord(i + 2).equals("(")){
            Decl(root);
        }
        while(isFuncDef()&&!ReadWord(i + 1).equals("main")){
            FuncDef(root);
        }
        MainFuncDef(root);
        root.print_node(ans);
//        curSymbolTable = curSymbolTable.parent;
//        curSymbolTable.output_symbols();
        output_to_file();
        return root;
    }

    public void output_to_file() throws IOException {
        File file = new File("output.txt");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(ans.toString());
        writer.close();
    }

    private TreeNode MainFuncDef(TreeNode root){
        TreeNode mainFuncDef = new TreeNode();
        addOccurLine(mainFuncDef,i);
        mainFuncDef.type = "MainFuncDef";
        mainFuncDef.fnode = root;
        root.childNodes.add(mainFuncDef);

        //读取当前函数名字和返回值信息，并加入到当前符号表中
        String funcName = "main";
        String funcType = "int";
        int decl_line = words.get(i + 1).line_num;
        //处理b类错误：重定义
        if(curSymbolTable.lookupCurSymbolTable(funcName)) {
            errors.append(decl_line).append(" b\n");
            sa_errors.addError(decl_line,"b");
            curSymbolTable.addNewSymbol(funcName,funcType,0,decl_line,0);
        }else{
            curSymbolTable.addNewSymbol(funcName,funcType,0,decl_line,1);
        }
        //创建新符号表，改变cur指向
        Symbol_Table newSymbolTable = new Symbol_Table(curSymbolTable,funcName);
        curSymbolTable = newSymbolTable;
        this.name2symboltable.put(funcName,newSymbolTable);
        mainFuncDef.symbol_table = curSymbolTable;

        PrintWord(i);PrintWord(i + 1);PrintWord(i + 2); //int main (
        i+=3;
        if(ReadWord(i).equals(")")){
            PrintWord(i++);// )
        }else{
            errors.append(words.get(i - 1).line_num).append(" j\n");//处理j类错误
            sa_errors.addError(words.get(i - 1).line_num,"j");
        }

        Block(mainFuncDef);
        mainFuncDef.print_node(ans);
        return mainFuncDef;
    }
    private TreeNode Decl(TreeNode treeNode){
        TreeNode decl = new TreeNode();
        addOccurLine(decl,i);
        decl.type = "Decl";
        decl.fnode = treeNode;
        treeNode.childNodes.add(decl);

        if(isConstDecl()){
            ConstDecl(decl);
        }else{
            VarDecl(decl);
        }
//        decl.print_node(ans);
        return decl;
    }
    private TreeNode ConstDecl(TreeNode treeNode){
        TreeNode constDecl = new TreeNode();
        addOccurLine(constDecl,i);
        constDecl.type = "ConstDecl";
        constDecl.fnode = treeNode;
        treeNode.childNodes.add(constDecl);

        PrintWord(i);i += 1;//const
        BType(constDecl);
        ConstDef(constDecl);
        while(ReadWord(i).equals(",")){
            PrintWord(i++);// ,
            ConstDef(constDecl);
        }
        if(ReadWord(i).equals(";")){
            PrintWord(i++);// ;
        }else{
            errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
            sa_errors.addError(words.get(i - 1).line_num,"i");
        }
        constDecl.print_node(ans);
        return constDecl;
    }

    private TreeNode BType(TreeNode treeNode){
        TreeNode bType = new TreeNode();
        addOccurLine(bType,i);
        bType.type = "BType";
        bType.fnode = treeNode;
        treeNode.childNodes.add(bType);

        PrintWord(i++);//int
        return bType;
    }

    private TreeNode FuncType(TreeNode treeNode){
        TreeNode funcType = new TreeNode();
        addOccurLine(funcType,i);
        funcType.type = "FuncType";
        funcType.fnode = treeNode;
        treeNode.childNodes.add(funcType);

        PrintWord(i++);//int|void
        funcType.print_node(ans);
        return funcType;
    }
    private ConstDef ConstDef(TreeNode treeNode){
        ConstDef constDef = new ConstDef();
        addOccurLine(constDef,i);
        constDef.type = "ConstDef";
        constDef.fnode = treeNode;
        treeNode.childNodes.add(constDef);
        constDef.symbol_table = curSymbolTable;

        String ident = ReadWord(i);
        int line_num = words.get(i).line_num;
        PrintWord(i++);//Ident
        constDef.name = ident;

        int dimension = 0;
        while(ReadWord(i).equals("[")){
            dimension ++;
            PrintWord(i++);// [
            ConstExp(constDef);
            if(ReadWord(i).equals("]")) {
                PrintWord(i++);// ]
            }else{
                errors.append(words.get(i - 1).line_num).append(" k\n");//检查是否有]
                sa_errors.addError(words.get(i - 1).line_num,"k");
            }

        }

        //处理b类错误：重定义
        if(curSymbolTable.lookupCurSymbolTable(ident)){
            errors.append(line_num).append(" b\n");
            sa_errors.addError(line_num,"b");
            curSymbolTable.addNewSymbol(ident,"const",dimension,line_num,0);//ident加入当前符号表
        }else{
            curSymbolTable.addNewSymbol(ident,"const",dimension,line_num,1);//ident加入当前符号表
        }


        PrintWord(i++);// =
        ConstInitVal(constDef);
        constDef.print_node(ans);
        return constDef;
    }
    private ConstInitVal ConstInitVal(TreeNode treeNode){
        ConstInitVal constInitVal = new ConstInitVal();
        addOccurLine(constInitVal,i);
        constInitVal.type = "ConstInitVal";
        constInitVal.fnode = treeNode;
        treeNode.childNodes.add(constInitVal);

        if(ReadWord(i).equals("{")){
            PrintWord(i++);// {
            if(!ReadWord(i).equals("}")){
                ConstInitVal(constInitVal);
                while(ReadWord(i).equals(",")){
                    PrintWord(i++);// ,
                    ConstInitVal(constInitVal);
                }
            }
            PrintWord(i++);// }
        }else{
            ConstExp(constInitVal);
        }
        constInitVal.print_node(ans);
        return constInitVal;
    }
    private ConstExp ConstExp(TreeNode treeNode){
        ConstExp constExp = new ConstExp();
        addOccurLine(constExp,i);
        constExp.type = "ConstExp";
        constExp.fnode = treeNode;
        treeNode.childNodes.add(constExp);
        AddExp(constExp);
        constExp.print_node(ans);
        return constExp;
    }
    private TreeNode VarDecl(TreeNode treeNode){
        TreeNode varDecl = new TreeNode();
        addOccurLine(varDecl,i);
        varDecl.type = "VarDecl";
        varDecl.fnode = treeNode;
        treeNode.childNodes.add(varDecl);

        BType(varDecl);
        VarDef(varDecl);
        while(ReadWord(i).equals(",")){
            PrintWord(i++);// ,
            VarDef(varDecl);
        }
        if(ReadWord(i).equals(";")){
            PrintWord(i++);// ;
        }else{
            errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
            sa_errors.addError(words.get(i-1).line_num,"i");
        }
        varDecl.print_node(ans);
        return varDecl;
    }

    private VarDef VarDef(TreeNode treeNode) {
        VarDef varDef = new VarDef();
        addOccurLine(varDef,i);
        varDef.type = "VarDef";
        varDef.fnode = treeNode;
        treeNode.childNodes.add(varDef);
        varDef.symbol_table = curSymbolTable;

        String ident = ReadWord(i);
        int line_num = words.get(i).line_num;
        varDef.name = ReadWord(i);
        PrintWord(i++);//ident

        int dimension = 0;
        while(ReadWord(i).equals("[")){
            dimension ++;
            PrintWord(i++);// [
            ConstExp(varDef);
            if(ReadWord(i).equals("]")){
                PrintWord(i++);// ]
            }else{
                errors.append(words.get(i-1).line_num).append(" k\n");//处理k类错误
                sa_errors.addError(words.get(i-1).line_num,"k");
            }
        }

        //处理b类错误：重定义
        if(curSymbolTable.lookupCurSymbolTable(ident)) {
            errors.append(line_num).append(" b\n");
            sa_errors.addError(line_num,"b");
            curSymbolTable.symbols.add(new Symbol(ident,"var",dimension,line_num,0));//当前声明的变量加入符号表
        }else{
            curSymbolTable.symbols.add(new Symbol(ident,"var",dimension,line_num,1));//当前声明的变量加入符号表
        }

        if(ReadWord(i).equals("=")){
            PrintWord(i++);// =
            InitVal(varDef);
        }
        varDef.print_node(ans);
        return varDef;
    }

    private InitVal InitVal(TreeNode treeNode) {
        InitVal initVal = new InitVal();
        addOccurLine(initVal,i);
        initVal.type = "InitVal";
        initVal.fnode = treeNode;
        treeNode.childNodes.add(initVal);
        if(ReadWord(i).equals("{")){
            PrintWord(i++);// {
//            while(ReadWord(i-1).equals("{")){
                if(!ReadWord(i).equals("}")){
                    InitVal(initVal);
                    while(ReadWord(i).equals(",")){
                        PrintWord(i++);// ,
                        InitVal(initVal);
                    }

                }
                    PrintWord(i++);// }

//            }
        }else{
            Exp(initVal);
        }


        initVal.print_node(ans);
        return initVal;
    }

    private TreeNode FuncDef(TreeNode treeNode){

        TreeNode funcDef = new TreeNode();
        addOccurLine(funcDef,i);
        funcDef.type = "FuncDef";
        funcDef.fnode = treeNode;
        treeNode.childNodes.add(funcDef);

        FuncType(funcDef);

        //读取当前函数名字和返回值信息，(并加入到当前符号表中)?
        String funcName = ReadWord(i);
        funcDef.content = funcName;
        String funcType = ReadWord(i-1);
        int decl_line = words.get(i).line_num;
        //处理b类错误：重定义
        if(curSymbolTable.lookupCurSymbolTable(funcName)) {
            errors.append(decl_line).append(" b\n");
            sa_errors.addError(decl_line,"b");
            if(funcType.equals("void"))
                curSymbolTable.addNewSymbol(funcName,funcType,-1,decl_line,0);
            else
                curSymbolTable.addNewSymbol(funcName,funcType,0,decl_line,0);

        }else{
            if(funcType.equals("void"))
                curSymbolTable.addNewSymbol(funcName,funcType,-1,decl_line,0);
            else
                curSymbolTable.addNewSymbol(funcName,funcType,0,decl_line,0);
        }

        //创建新符号表，改变cur指向
        Symbol_Table newSymbolTable = new Symbol_Table(curSymbolTable,funcName);
        curSymbolTable = newSymbolTable;
        this.name2symboltable.put(funcName,newSymbolTable);
        funcDef.symbol_table = curSymbolTable;

        PrintWord(i++);//ident
        PrintWord(i++);// (
        if(!ReadWord(i).equals(")")&&!ReadWord(i).equals("{")){
            FuncFParams(funcDef);
        }
        if(ReadWord(i).equals(")")){
            PrintWord(i++);// )
        }else{
            errors.append(words.get(i - 1).line_num).append(" j\n");//处理j类错误
            sa_errors.addError(words.get(i-1).line_num,"j");
        }
        Block(funcDef);
        funcDef.print_node(ans);
        curSymbolTable = curSymbolTable.parent;
        return funcDef;
    }

    private TreeNode FuncFParams(TreeNode treeNode){
        TreeNode funcFParams = new TreeNode();
        addOccurLine(funcFParams,i);
        funcFParams.type = "FuncFParams";
        funcFParams.fnode = treeNode;
        treeNode.childNodes.add(funcFParams);

        FuncFParam(funcFParams);
        while (ReadWord(i).equals(",")){
            PrintWord(i++);// ,
            FuncFParam(funcFParams);
        }
        funcFParams.print_node(ans);
        return funcFParams;
    }

    private TreeNode FuncFParam(TreeNode treeNode){
        TreeNode funcFParam = new TreeNode();
        addOccurLine(funcFParam,i);
        funcFParam.type = "FuncFParam";
        funcFParam.fnode = treeNode;
        treeNode.childNodes.add(funcFParam);
        funcFParam.symbol_table = curSymbolTable;

        BType(funcFParam);

        String funcFParam_name = ReadWord(i);
        int decl_line = words.get(i).line_num;
        int dimension = 0;

        PrintWord(i++);//ident
        funcFParam.name = ReadWord(i-1);
        if(ReadWord(i).equals("[")){
            PrintWord(i++);// [
            if(ReadWord(i).equals("]")){
                PrintWord(i++);// ]
            }else{
                errors.append(words.get(i - 1).line_num).append(" k\n");//处理k类错误
                sa_errors.addError(words.get(i-1).line_num,"k");
            }

            dimension ++;
            if(ReadWord(i).equals("[")){
                PrintWord(i++);// [
                dimension++;
                ConstExp(funcFParam);
                if(ReadWord(i).equals("]")){
                    PrintWord(i++);// ]
                }else{
                    errors.append(words.get(i - 1).line_num).append(" k\n");//处理k类错误
                    sa_errors.addError(words.get(i-1).line_num,"k");
                }
            }
        }

        Symbol thisParam = new Symbol(funcFParam_name,"var",dimension,decl_line);
        //处理b类错误：重定义
        if(curSymbolTable.lookupCurSymbolTable(funcFParam_name)) {
            errors.append(decl_line).append(" b\n");
            sa_errors.addError(decl_line,"b");
            thisParam.valid = 0;
            curSymbolTable.symbols.add(thisParam);//ident加入当前符号表
        }else{
            thisParam.valid = 1;
            curSymbolTable.symbols.add(thisParam);//ident加入当前符号表
        }

        //形参信息加入上一层符号表（储存本函数的symbol条目）
        Symbol this_func = curSymbolTable.lookupSymbol(curSymbolTable.funcName,-9);//行数不重要
        if(curSymbolTable.parent.occurTimes(curSymbolTable.funcName)==1)this_func.params.add(thisParam);
        funcFParam.print_node(ans);
        return funcFParam;
    }
    private TreeNode Block(TreeNode treeNode){
        TreeNode block = new TreeNode();
        addOccurLine(block,i);
        block.type = "Block";
        block.fnode = treeNode;
        treeNode.childNodes.add(block);

        //建立新符号表，并改变cur指向
        boolean flag = false;
        String funcName = "block";
        if(block.fnode.type.equals("MainFuncDef")){
            funcName = "main";
            block.symbol_table = curSymbolTable;
        }
        if(!block.fnode.type.equals("FuncDef")&&!block.fnode.type.equals("MainFuncDef")){
            Symbol_Table newSymbolTable = new Symbol_Table(curSymbolTable,funcName);
            curSymbolTable = newSymbolTable;
            block.symbol_table = curSymbolTable;
            flag = true;
        }


        PrintWord(i++);// {
        //g类错误
        String next_word_of_return = "";
        int num = 1;//记录block内部条目个数
        int return_num = 0;//记录return语句出现的条目序号
        while(!ReadWord(i).equals("}")){
            if(ReadWord(i).equals("return")){
                return_num = num;
                next_word_of_return = ReadWord(i+1);

            }
            BlockItem(block);
            num++;
        }

//        Symbol curFunc = curSymbolTable.parent.lookupValidSymbol(curSymbolTable.funcName,words.get(i).line_num);//行号不重要
        Symbol curFunc = curSymbolTable.parent.lookupLastSymbol(curSymbolTable.funcName,0);
        if((curFunc!=null)&&((return_num < num - 1)||(next_word_of_return.equals(";"))||(return_num == 0))&&(curFunc.type.equals("int"))){//return不是block里的最后一项且函数是有返回值的函数,g类错误;
            errors.append(words.get(i).line_num).append(" g\n");
            sa_errors.addError(words.get(i).line_num,"g");
        }
        //g类错误处理完毕
        PrintWord(i++);// }
        //如果是没有return语句的void函数，这里加一个结点，方便输出return中间代码
        if(block.fnode.type.equals("FuncDef")&&return_num == 0){
            TreeNode return_stmt = new TreeNode();
            addOccurLine(return_stmt,i);
            return_stmt.type = "Stmt";
            return_stmt.content = "return";
            return_stmt.fnode = block;
            return_stmt.symbol_table = curSymbolTable;
            block.childNodes.add(return_stmt);
        }
        block.print_node(ans);
        if(flag){
            curSymbolTable = curSymbolTable.parent;
        }
        return block;

    }

    private TreeNode BlockItem(TreeNode treeNode){
        TreeNode blockItem = new TreeNode();
        addOccurLine(blockItem,i);
        blockItem.type = "BlockItem";
        blockItem.fnode = treeNode;
        treeNode.childNodes.add(blockItem);

        if(ReadWord(i).equals("const")||ReadWord(i).equals("int")){
            Decl(blockItem);
        }
        else{
            Stmt(blockItem);
        }
        return blockItem;
    }

    private TreeNode Stmt(TreeNode treeNode){
        TreeNode stmt = new TreeNode();
        addOccurLine(stmt,i);
        stmt.type = "Stmt";
        stmt.fnode = treeNode;
        treeNode.childNodes.add(stmt);
        stmt.symbol_table = curSymbolTable;

        if(isPrintf()){
            stmt.content = ReadWord(i + 2);
            PrintWord(i);PrintWord(i + 1);PrintWord(i + 2);i +=3;//printf ( formatString
            //处理l类错误
            int expNum = 0;//记录printf语句后跟随表达式个数
            int expedNum = words.get(i-1).d_num;//记录应该有的个数
            int line = words.get(i-3).line_num;//记录printf出现行数
            while(ReadWord(i).equals(",")){
                PrintWord(i);i++;// ,
                expNum++;
                Exp(stmt);
            }
            if(expNum!=expedNum){
                errors.append(line).append(" l\n");
                sa_errors.addError(line,"l");
            }
            //l类错误处理完毕
            if(ReadWord(i).equals(")")){
                PrintWord(i++);// )
            }else{
                errors.append(words.get(i - 1).line_num).append(" j\n");//处理j类错误
                sa_errors.addError(words.get(i-1).line_num,"j");
            }

            if(ReadWord(i).equals(";")){
                PrintWord(i++);// ;
            }else{
                errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
                sa_errors.addError(words.get(i-1).line_num,"i");
            }
        }else if(isReturn()){
            stmt.content = ReadWord(i);//"return"
            //处理f类错误(先到上一级取查找当前函数信息)
            String curFuncName = curSymbolTable.funcName;
            Symbol curFunc = curSymbolTable.parent.lookupSymbol(curFuncName,words.get(i).line_num);//行数不重要

            PrintWord(i);i++;//return
            if(!ReadWord(i).equals(";")){
                Symbol curFuncRec = curFunc;
                String curFuncNameRec = curFuncName;
                Symbol_Table curSymbolTableRec = curSymbolTable;
                while(curFunc == null){
                    curFuncName = curSymbolTable.parent.funcName;
                    curFunc = curSymbolTable.parent.lookupSymbol(curFuncName,-9);//行数不重要
                    curSymbolTable = curSymbolTable.parent;
                }
                if(curFunc.type.equals("void")&&(ReadType(i).equals("IDENFR")||ReadType(i).equals("INTCON")||ReadType(i).equals("PLUS")||ReadType(i).equals("MINU")||ReadType(i).equals("LPARENT")||ReadType(i).equals("MULT")||ReadType(i).equals("DIV")||ReadType(i).equals("MOD"))){
                    errors.append(words.get(i-1).line_num).append(" f\n");//如果是void函数不应该有exp
                    sa_errors.addError(words.get(i-1).line_num,"f");
                }
                curFunc = curFuncRec;
                curFuncName = curFuncNameRec;
                curSymbolTable = curSymbolTableRec;
                if(ReadType(i).equals("IDENFR")||ReadType(i).equals("INTCON")||ReadType(i).equals("PLUS")||ReadType(i).equals("MINU")||ReadType(i).equals("LPARENT")||ReadType(i).equals("MULT")||ReadType(i).equals("DIV")||ReadType(i).equals("MOD")){
                    Exp(stmt);
                }
            }
            if(ReadWord(i).equals(";")){
                PrintWord(i++);// ;
            }else{
                errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
                sa_errors.addError(words.get(i-1).line_num,"i");
            }
//            curSymbolTable.output_symbols();

        }else if(isBreak()){
            PrintWord(i++); //break
            stmt.content = "break";
            //处理m类错误
            if(cycleDepth <= 0){
                errors.append(words.get(i-1).line_num).append(" m\n");
                sa_errors.addError(words.get(i-1).line_num,"m");

            }
            //处理m类错误结束
            if(ReadWord(i).equals(";")){
                PrintWord(i++);// ;
            }else{
                errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
                sa_errors.addError(words.get(i-1).line_num,"i");

            }
        }else if (isContinue()){
            PrintWord(i++); //continue
            stmt.content = "continue";
            //处理m类错误
            if(cycleDepth <= 0){
                errors.append(words.get(i-1).line_num).append(" m\n");
                sa_errors.addError(words.get(i-1).line_num,"m");

            }
            //处理m类错误结束
            if(ReadWord(i).equals(";")){
                PrintWord(i++);// ;
            }else{
                errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
                sa_errors.addError(words.get(i-1).line_num,"i");

            }
        }else if(isWhile()){
            PrintWord(i);PrintWord(i + 1);i += 2; // while (

            stmt.content = "while";
            Cond(stmt);

            if(ReadWord(i).equals(")")){
                PrintWord(i++);// )
            }else{
                errors.append(words.get(i - 1).line_num).append(" j\n");//处理j类错误
                sa_errors.addError(words.get(i-1).line_num,"j");

            }

            cycleDepth ++;
            Stmt(stmt);
            cycleDepth --;

        }else if(isIf()){
            PrintWord(i);PrintWord(i + 1);i += 2;// if (
            stmt.content = "if";
            Cond(stmt);
            if(ReadWord(i).equals(")")){
                PrintWord(i++);// )
            }else{
                errors.append(words.get(i - 1).line_num).append(" j\n");//处理j类错误
                sa_errors.addError(words.get(i-1).line_num,"j");

            }
            Stmt(stmt);
            if(ReadWord(i).equals("else")){
                PrintWord(i);i++;//else
                Stmt(stmt);
            }
        }else if(isLBRACE()){
            Block(stmt);
        }else if(hasASSIGN()){
            TreeNode this_lVal = LVal(stmt);
            //处理h类错误
            String ident =this_lVal.content;
            int use_line = this_lVal.line;
            Symbol this_symbol = curSymbolTable.lookupSymbol(ident,use_line);
            if(this_symbol != null){
                if(this_symbol.type.equals("const")){
                    errors.append(use_line).append(" h\n");
                    sa_errors.addError(use_line,"h");

                }
            }

            PrintWord(i);i++;// =
            if(ReadWord(i).equals("getint")){
                stmt.content = "read";
                PrintWord(i++);//getint
                PrintWord(i++);// (
                if(ReadWord(i).equals(")")){
                    PrintWord(i++);// )
                }else{
                    errors.append(words.get(i - 1).line_num).append(" j\n");//处理j类错误
                    sa_errors.addError(words.get(i-1).line_num,"j");

                }
                if(ReadWord(i).equals(";")){
                    PrintWord(i++);// ;
                }else{
                    errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
                    sa_errors.addError(words.get(i-1).line_num,"i");

                }
            }else{//赋值语句
                stmt.content = "assign";
                Exp(stmt);
                if(ReadWord(i).equals(";")){
                    PrintWord(i++);// ;
                }else{
                    errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
                    sa_errors.addError(words.get(i-1).line_num,"i");

                }
            }
        }else{
            if(!ReadWord(i).equals(";")){
                Exp(stmt);
            }
            if(ReadWord(i).equals(";")){
                PrintWord(i++);// ;
            }else{
                errors.append(words.get(i - 1).line_num).append(" i\n");//处理i类错误
                sa_errors.addError(words.get(i-1).line_num,"i");

            }
        }
        stmt.print_node(ans);

        return stmt;
    }
    private TreeNode Cond(TreeNode treeNode){
        TreeNode cond = new TreeNode();
        addOccurLine(cond,i);
        cond.type = "Cond";
        cond.fnode = treeNode;
        cond.symbol_table = curSymbolTable;
        treeNode.childNodes.add(cond);
        LOrExp(cond);
        cond.print_node(ans);
        return cond;
    }
    private TreeNode LOrExp(TreeNode treeNode){
        //左递归
        TreeNode lOrExp_ = new TreeNode();
        addOccurLine(lOrExp_,i);
        lOrExp_.type = "LOrExp";
        lOrExp_.symbol_table = curSymbolTable;
        LAndExp(lOrExp_);
        lOrExp_.print_node(ans);
        while(ReadWord(i).equals("||")){
            PrintWord(i++);// ||

            TreeNode lOrExp__ = new TreeNode();
            addOccurLine(lOrExp__,i);
            lOrExp_.fnode = lOrExp__;
            lOrExp__.childNodes.add(lOrExp_);
            lOrExp__.type = "LOrExp";
            lOrExp__.symbol_table = curSymbolTable;
            lOrExp__.content = "||";
            LAndExp(lOrExp__);
            lOrExp__.print_node(ans);
            lOrExp_ = lOrExp__;
        }

        lOrExp_.fnode = treeNode;
        treeNode.childNodes.add(lOrExp_);
//        lOrExp_.print_node(ans);
        return lOrExp_;
    }

    private TreeNode LAndExp(TreeNode treeNode) {
        //左递归
        TreeNode lAndExp = new TreeNode();
        addOccurLine(lAndExp,i);
        lAndExp.type = "LAndExp";
        lAndExp.symbol_table = curSymbolTable;
        EqExp(lAndExp);
        lAndExp.print_node(ans);
        while(ReadWord(i).equals("&&")){
            PrintWord(i++);// &&

            TreeNode lAndExp_ = new TreeNode();
            addOccurLine(lAndExp_,i);
            lAndExp.fnode = lAndExp_;
            lAndExp_.childNodes.add(lAndExp);
            lAndExp_.type = "LAndExp";
            lAndExp_.symbol_table = curSymbolTable;
            lAndExp_.content = "&&";
            EqExp(lAndExp_);
            lAndExp_.print_node(ans);
            lAndExp = lAndExp_;
        }

        lAndExp.fnode = treeNode;
        treeNode.childNodes.add(lAndExp);
//        lAndExp.print_node(ans);
        return lAndExp;
    }

    private TreeNode EqExp(TreeNode treeNode) {
        //左递归
        TreeNode eqExp = new TreeNode();
        addOccurLine(eqExp,i);
        eqExp.type = "EqExp";
        eqExp.symbol_table = curSymbolTable;

        RelExp(eqExp);
        eqExp.print_node(ans);
        while(ReadWord(i).equals("==")||ReadWord(i).equals("!=")){
            PrintWord(i++);// ==|!=

            TreeNode eqExp_ = new TreeNode();
            addOccurLine(eqExp_,i);
            eqExp.fnode = eqExp_;
            eqExp_.childNodes.add(eqExp);
            eqExp_.type = "EqExp";
            eqExp_.symbol_table = curSymbolTable;
            eqExp_.content = ReadWord(i-1);
            RelExp(eqExp_);
            eqExp_.print_node(ans);
            eqExp = eqExp_;
        }

        eqExp.fnode = treeNode;
        treeNode.childNodes.add(eqExp);
//        eqExp.print_node(ans);
        return eqExp;
    }

    private TreeNode RelExp(TreeNode treeNode) {
        //左递归
        TreeNode relExp = new TreeNode();
        addOccurLine(relExp,i);
        relExp.type = "RelExp";
        relExp.symbol_table = curSymbolTable;

        AddExp(relExp);
        relExp.print_node(ans);
        while(ReadWord(i).equals(">")||ReadWord(i).equals("<")||ReadWord(i).equals(">=")||ReadWord(i).equals("<=")){
            PrintWord(i++); // >|<=

            TreeNode relExp_ = new TreeNode();
            addOccurLine(relExp_,i);
            relExp.fnode = relExp_;
            relExp_.childNodes.add(relExp);
            relExp_.type = "RelExp";
            relExp_.symbol_table = curSymbolTable;
            relExp_.content = ReadWord(i-1);
            AddExp(relExp_);
            relExp_.print_node(ans);
            relExp = relExp_;
        }
        relExp.fnode = treeNode;
        treeNode.childNodes.add(relExp);
//        relExp.print_node(ans);
        return relExp;
    }

    private Exp Exp(TreeNode treeNode){
        Exp exp = new Exp();
        addOccurLine(exp,i);
        exp.type = "Exp";
        exp.fnode = treeNode;
        treeNode.childNodes.add(exp);
        TreeNode addexp = AddExp(exp);
        exp.params = addexp.params;
        exp.dimension = addexp.dimension;
        exp.print_node(ans);
//                System.out.println(exp.ident);
        return exp;
    }
    private AddExp AddExp(TreeNode treeNode){
        //左递归文法的处理
        AddExp addExp_ = new AddExp();
        addOccurLine(addExp_,i);
        addExp_.type = "AddExp";
        addExp_.symbol_table = curSymbolTable;
        MulExp mulExp = MulExp(addExp_);
        addExp_.print_node(ans);

        while(ReadWord(i).equals("+")||ReadWord(i).equals("-")){
            PrintWord(i++);// +|-

            //维护addExp_始终是最顶层的节点
            AddExp addExp__ = new AddExp();
            addOccurLine(addExp__,i);
            addExp__.type = "AddExp";
            addExp__.childNodes.add(addExp_);
            addExp_.fnode = addExp__;
            addExp__.content = ReadWord(i-1);
            addExp__.symbol_table = curSymbolTable;

            MulExp(addExp__);
            addExp__.print_node(ans);
            addExp_ = addExp__;
        }

        addExp_.fnode = treeNode;
        treeNode.childNodes.add(addExp_);
        addExp_.params = mulExp.params;
        addExp_.dimension = mulExp.dimension;
//        addExp_.print_node(ans);
        return addExp_;
    }
    private MulExp MulExp(TreeNode treeNode){
        MulExp mulExp = new MulExp();
        addOccurLine(mulExp,i);
        mulExp.symbol_table = curSymbolTable;
        mulExp.type = "MulExp";
        UnaryExp unaryExp = UnaryExp(mulExp);
//        mulExp.ident = unaryExp.ident;
//        mulExp.value = unaryExp.value;
        mulExp.print_node(ans);
        while (ReadWord(i).equals("*")||ReadWord(i).equals("/")||ReadWord(i).equals("%")){
            PrintWord(i++);// *|/|%
            //维护mulExp最顶层节点
            MulExp mulExp_ = new MulExp();
            addOccurLine(mulExp_,i);
            mulExp_.type = "MulExp";
            mulExp_.childNodes.add(mulExp);
            mulExp.fnode = mulExp_;
            mulExp_.content = ReadWord(i-1);
            mulExp_.symbol_table = curSymbolTable;

            UnaryExp(mulExp_);
            mulExp_.print_node(ans);
            mulExp = mulExp_;
        }

        mulExp.fnode = treeNode;
        treeNode.childNodes.add(mulExp);
        mulExp.params = unaryExp.params;
        mulExp.dimension = unaryExp.dimension;
//        mulExp.print_node(ans);
        return mulExp;
    }

    private UnaryExp UnaryExp(TreeNode treeNode){
        UnaryExp unaryExp = new UnaryExp();
        addOccurLine(unaryExp,i);
        unaryExp.fnode = treeNode;
        treeNode.childNodes.add(unaryExp);
        unaryExp.type = "UnaryExp";
        unaryExp.symbol_table = curSymbolTable;

        if(ReadWord(i).equals("+")||ReadWord(i).equals("-")||ReadWord(i).equals("!")){
            unaryExp.content = ReadWord(i);
            UnaryOp(unaryExp);
            TreeNode unaryExp_new = UnaryExp(unaryExp);
            unaryExp.params = unaryExp_new.params;
            unaryExp.dimension = unaryExp_new.dimension;
        }else if((!ReadWord(i).equals("("))&&ReadWord(i+1).equals("(")){
            //处理c类错误
            Symbol curFunc = null;
            String name = ReadWord(i);
            int use_line = words.get(i).line_num;
            if(curSymbolTable.lookupSymbol(name,use_line)==null){
                errors.append(use_line).append(" c\n");
                sa_errors.addError(use_line,"c");


            }else{
                curFunc = curSymbolTable.lookupSymbol(name,-9);
                unaryExp.dimension = curFunc.dimension;
            }

            unaryExp.content = ReadWord(i);
            PrintWord(i++);//ident
            PrintWord(i++);// (

            if(!ReadWord(i).equals(")")&&!ReadWord(i).equals(";")){
                TreeNode funcRParams = FuncRParams(unaryExp);
                unaryExp.params = funcRParams.params;
//                System.out.println(funcRParams.dimensions);
            }
            if(ReadWord(i).equals(")")){
                PrintWord(i++);// )
            }else{
                errors.append(words.get(i - 1).line_num).append(" j\n");//处理j类错误,缺 )
                sa_errors.addError(words.get(i-1).line_num,"j");

            }

//            curFunc = curSymbolTable.lookupSymbol(name,0);


            if(curFunc!=null){
                //处理de类错误（调用函数的唯一位置）
                assert unaryExp.params!=null;
                if(unaryExp.params.size()!=curFunc.params.size()){//参数个数不匹配,d类错误（左为实际参数个数，右为定义参数个数）
                    errors.append(words.get(i-2).line_num).append(" d\n");
                    sa_errors.addError(words.get(i-2).line_num,"d");

                }

//            unaryExp.params = curFunc.params;
                for(int it = 0; it < curFunc.params.size()&&it < unaryExp.params.size();it ++){
                    if(unaryExp.params.get(it).dimension!=curFunc.params.get(it).dimension){
                        errors.append(words.get(i-2).line_num).append(" e\n");//参数类型不匹配,e类错误
                        sa_errors.addError(words.get(i-2).line_num,"e");

                        break;
                    }else if(unaryExp.params.get(it).dimension == -1){
                        errors.append(words.get(i-2).line_num).append(" e\n");//参数类型不匹配,e类错误
                        sa_errors.addError(words.get(i-2).line_num,"e");

                        break;
                    }
                }
            }
        }else{
            PrimaryExp primaryExp = PrimaryExp(unaryExp);
            unaryExp.params = primaryExp.params;
            unaryExp.ident = primaryExp.ident;
            unaryExp.value = primaryExp.value;
            unaryExp.dimension = primaryExp.dimension;
        }

        unaryExp.print_node(ans);
//        System.out.println(unaryExp.ident);
        return unaryExp;
    }

    private TreeNode UnaryOp(TreeNode treeNode) {
        TreeNode unaryOp = new TreeNode();
        addOccurLine(unaryOp,i);
        unaryOp.fnode = treeNode;
        treeNode.childNodes.add(unaryOp);
        unaryOp.type = "UnaryOp";

        PrintWord(i++);// +|-|!
        unaryOp.print_node(ans);
        return unaryOp;
    }

    private PrimaryExp PrimaryExp(TreeNode treeNode){
        PrimaryExp primaryExp = new PrimaryExp();
        addOccurLine(primaryExp,i);
        primaryExp.fnode = treeNode;
        treeNode.childNodes.add(primaryExp);
        primaryExp.type = "PrimaryExp";
        if(ReadWord(i).equals("(")){
            PrintWord(i++);// (
            Exp exp = Exp(primaryExp);
            primaryExp.ident = exp.ident;
            primaryExp.value = exp.value;
            primaryExp.dimension = exp.dimension;
            if(ReadWord(i).equals(")")){
                PrintWord(i++);// )
            }else{
                errors.append(words.get(i - 1).line_num).append(" j\n");//处理j类错误
                sa_errors.addError(words.get(i - 1).line_num,"j");
            }
        }else if(ReadType(i).equals("INTCON")){
            Number number = Number(primaryExp);
            primaryExp.value = number.value;
            primaryExp.dimension = 0;
        }else{
            LVal lVal = LVal(primaryExp);
            primaryExp.ident = lVal.ident;
            primaryExp.dimension = lVal.dimension;
        }
        primaryExp.print_node(ans);

        return primaryExp;
    }

    private LVal LVal(TreeNode treeNode){
        LVal lVal = new LVal();
        addOccurLine(lVal,i);
        lVal.fnode = treeNode;
        treeNode.childNodes.add(lVal);
        lVal.type = "LVal";
        lVal.content = ReadWord(i);//储存ident的内容 用于查表
        lVal.line = words.get(i).line_num;//储存ident使用行数
        lVal.ident = lVal.content;
        lVal.symbol_table = curSymbolTable;

        //处理c类错误
        if(curSymbolTable.lookupSymbol(lVal.content,lVal.line) == null){
            errors.append(lVal.line).append(" c\n");
            sa_errors.addError(lVal.line,"c");

        }

        PrintWord(i++);//ident
        String ident = ReadWord(i-1);
        int dimension = 0;
        while(ReadWord(i).equals("[")){
            dimension++;
            PrintWord(i++);// [
            Exp(lVal);
            if(ReadWord(i).equals("]")){
                PrintWord(i++);// ]
            }else{
                errors.append(words.get(i - 1).line_num).append(" k\n");//处理k类错误
                sa_errors.addError(words.get(i-1).line_num,"k");

            }
        }
        lVal.print_node(ans);
        if(curSymbolTable.lookupSymbol(ident,-9)!=null)
            lVal.dimension = curSymbolTable.lookupSymbol(ident,-9).dimension - dimension;
//        System.out.println(lVal.ident);
        return lVal;
    }
    private Number Number(TreeNode treeNode){
        Number number = new Number();
        addOccurLine(number,i);
        number.type = "Number";
        number.fnode = treeNode;
        treeNode.childNodes.add(number);
        number.value = Integer.parseInt(ReadWord(i));
        PrintWord(i++);//intconst
        number.print_node(ans);
        return number;
    }
    private TreeNode FuncRParams(TreeNode treeNode){
        TreeNode funcRParams = new TreeNode();
        addOccurLine(funcRParams,i);
        funcRParams.type = "FuncRParams";
        funcRParams.fnode = treeNode;
        treeNode.childNodes.add(funcRParams);
        funcRParams.symbol_table = curSymbolTable;

        TreeNode exp = Exp(funcRParams);
        funcRParams.params.add(new Param(exp.dimension));
        while(ReadWord(i).equals(",")){
            PrintWord(i++);// ,
            TreeNode exp_ = Exp(funcRParams);
//            if(curSymbolTable.lookupCurSymbolTable(ReadWord(i))){
//                Symbol this_symbol = curSymbolTable.lookupSymbol(ReadWord(i),0);
//                funcRParams.params.add(new Param(this_symbol.dimension));
//
//            }else{
//                funcRParams.params.add(new Param(exp_.dimension));
//            }
         /*   j = i;
            if(ReadWord(j).equals(")")){
                while(!ReadWord(j).equals("("))
                    j--;
            }
            if(curSymbolTable.lookupSymbol(ReadWord(j-1),0) != null&&(curSymbolTable.lookupSymbol(ReadWord(j-1),0).type.equals("void")||curSymbolTable.lookupSymbol(ReadWord(j-1),0).type.equals("int"))){
                Symbol this_symbol = curSymbolTable.lookupSymbol(ReadWord(j-1),0);
                if(this_symbol.type.equals("void")){
                    funcRParams.params.add(new Param(-1));
                }else{
                    funcRParams.params.add(new Param(this_symbol.dimension));
                }
            }else{
                funcRParams.params.add(new Param(exp_.dimension));
            }*/
            funcRParams.params.add(new Param(exp_.dimension));

        }

        funcRParams.print_node(ans);
        return funcRParams;
    }

    public boolean isDecl(){
        return isConstDecl()||isVarDecl();
    }
    public boolean isFuncDef(){
        String this_word = ReadWord(i);
        return this_word.equals("int")||this_word.equals("void");
    }

    public boolean isPrintf(){
        return ReadWord(i).equals("printf");
    }
    public boolean isReturn(){
        return ReadWord(i).equals("return");
    }
    public boolean isBreak(){
        return ReadWord(i).equals("break");
    }
    public boolean isContinue(){
        return ReadWord(i).equals("continue");
    }
    public boolean isWhile(){
        return ReadWord(i).equals("while");
    }
    public boolean isIf(){
        return ReadWord(i).equals("if");
    }
    public boolean isLBRACE(){
        return ReadWord(i).equals("{");
    }
    public boolean hasASSIGN(){
        int j = i;
        while((!ReadWord(i).equals(";"))&&words.get(i).line_num == words.get(j).line_num){
            if(ReadWord(i).equals("=")){
                i = j;
                return true;
            }
            i++;
        }
        i = j;
        return false;
    }

    public boolean isConstDecl(){
        String this_type = this.ReadType(i);
        return this_type.equals("CONSTTK");
    }
    public boolean isVarDecl(){
        String this_type = this.ReadType(i);
        return this_type.equals("INTTK");
    }
    public String ReadWord(int i){
        return words.get(i).content;
    }
    public String ReadType(int i){
        return words.get(i).type;
    }
    public void PrintWord(int i){
//        System.out.println(ReadType(i) + " " + ReadWord(i));
        String str = ReadType(i) + " " + ReadWord(i) + '\n';
        ans.append(str);
    }
    public void addOccurLine(TreeNode t,int i){
        t.occur_line = words.get(i).line_num;
    }
}

class TreeNode {
    TreeNode fnode = null;
    List<TreeNode> childNodes = new ArrayList<>();
    String type = "";
    String content = "";
    int line = 0;
    List<Param> params = new ArrayList<>();//对于函数来说它的形参个数和维度记录
    List<RParam> rParams = new ArrayList<>();
    int dimension;//对LVal来说变量的维度
    Symbol_Table symbol_table = new Symbol_Table();
    public void print_node(StringBuilder ans){
//        System.out.println("<" + type + ">");
        String str = "<" + type + ">" + '\n';
        ans.append(str);
    }
    int value;
    String ident;//t0,t1...
    String name;//常变量声明
    boolean con = false;//是否是常量（继承属性）
    int occur_line = -1;

    boolean logic = false;
    CondExp condExp = new CondExp();
    String label_begin = "";
    String label_end = "";

    boolean subCond = false;
    List<MidCode> midCodes_hidden = new ArrayList<>();
    StringBuilder midCodeOutput_hidden = new StringBuilder();

}

class VarDef extends TreeNode{
//    String ident = "";
}

class ConstDef extends TreeNode{
//    String ident = "";
}

class Exp extends TreeNode{
//    int value;
//    String ident;//t0,t1...
}

class ConstExp extends TreeNode{
//    int value;
//    String ident;//t0,t1...
}
class AddExp extends Exp{}
class MulExp extends Exp{}
class UnaryExp extends Exp{}
class PrimaryExp extends Exp{}
class LVal extends Exp{}
class Number extends Exp{}
class ConstInitVal extends TreeNode{
//    int value;
//    String ident;
}
class InitVal extends TreeNode{
//    int value;
//    String ident;
}





