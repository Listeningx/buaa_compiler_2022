import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Syntactic_analysis {
    StringBuilder ans = new StringBuilder();
    int i = 0;
    int sum = 0;
    int cycleDepth = 0;//记录循环层数，错误处理用到
    StringBuilder errors = new StringBuilder();
    Error sa_errors = new Error();
    Symbol_Table curSymbolTable = new Symbol_Table();
    ArrayList<Word> words = new ArrayList<>();
    TreeNode root = new TreeNode();
    Identify_words iw = new Identify_words();
    public Syntactic_analysis(ArrayList<Word> words){
        this.words = words;
        sum = words.size();
        root.type = "CompUnit";
        Symbol_Table root_table = new Symbol_Table(null,"out");//初始化根符号表，父节点是null
        curSymbolTable = root_table;//设置当前符号表指向根符号表

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
        bType.type = "BType";
        bType.fnode = treeNode;
        treeNode.childNodes.add(bType);

        PrintWord(i++);//int
        return bType;
    }

    private TreeNode FuncType(TreeNode treeNode){
        TreeNode funcType = new TreeNode();
        funcType.type = "FuncType";
        funcType.fnode = treeNode;
        treeNode.childNodes.add(funcType);

        PrintWord(i++);//int|void
        funcType.print_node(ans);
        return funcType;
    }
    private TreeNode ConstDef(TreeNode treeNode){
        TreeNode constDef = new TreeNode();
        constDef.type = "ConstDef";
        constDef.fnode = treeNode;
        treeNode.childNodes.add(constDef);

        String ident = ReadWord(i);
        int line_num = words.get(i).line_num;
        PrintWord(i++);//Ident

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
    private TreeNode ConstInitVal(TreeNode treeNode){
        TreeNode constInitVal = new TreeNode();
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
    private TreeNode ConstExp(TreeNode treeNode){
        TreeNode constExp = new TreeNode();
        constExp.type = "ConstExp";
        constExp.fnode = treeNode;
        treeNode.childNodes.add(constExp);
        AddExp(constExp);
        constExp.print_node(ans);
        return constExp;
    }
    private TreeNode VarDecl(TreeNode treeNode){
        TreeNode varDecl = new TreeNode();
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

    private TreeNode VarDef(TreeNode treeNode) {
        TreeNode varDef = new TreeNode();
        varDef.type = "VarDef";
        varDef.fnode = treeNode;
        treeNode.childNodes.add(varDef);

        String ident = ReadWord(i);
        int line_num = words.get(i).line_num;
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

    private TreeNode InitVal(TreeNode treeNode) {
        TreeNode initVal = new TreeNode();
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
        funcDef.type = "FuncDef";
        funcDef.fnode = treeNode;
        treeNode.childNodes.add(funcDef);

        FuncType(funcDef);

        //读取当前函数名字和返回值信息，并加入到当前符号表中
        String funcName = ReadWord(i);
        String funcType = ReadWord(i-1);
        int decl_line = words.get(i).line_num;
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
        funcFParam.type = "FuncFParam";
        funcFParam.fnode = treeNode;
        treeNode.childNodes.add(funcFParam);

        BType(funcFParam);

        String funcFParam_name = ReadWord(i);
        int decl_line = words.get(i).line_num;
        int dimension = 0;

        PrintWord(i++);//ident
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

        //处理b类错误：重定义
        if(curSymbolTable.lookupCurSymbolTable(funcFParam_name)) {
            errors.append(decl_line).append(" b\n");
            sa_errors.addError(decl_line,"b");
            curSymbolTable.addNewSymbol(funcFParam_name,"var",dimension,decl_line,0);//ident加入当前符号表
        }else{
            curSymbolTable.addNewSymbol(funcFParam_name,"var",dimension,decl_line,1);//ident加入当前符号表
        }

        //形参信息加入上一层符号表（储存本函数的symbol条目）
        Symbol this_func = curSymbolTable.lookupSymbol(curSymbolTable.funcName,0);//行数不重要
        if(curSymbolTable.parent.occurTimes(curSymbolTable.funcName)==1)this_func.params.add(new Param(dimension));
        funcFParam.print_node(ans);
        return funcFParam;
    }
    private TreeNode Block(TreeNode treeNode){
        TreeNode block = new TreeNode();
        block.type = "Block";
        block.fnode = treeNode;
        treeNode.childNodes.add(block);

        //建立新符号表，并改变cur指向
        boolean flag = false;
        String funcName = "block";
        if(block.fnode.type.equals("MainFuncDef")){
            funcName = "main";
        }
        if(!block.fnode.type.equals("FuncDef")){
            Symbol_Table newSymbolTable = new Symbol_Table(curSymbolTable,funcName);
            curSymbolTable = newSymbolTable;
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
        block.print_node(ans);
        if(flag){
            curSymbolTable = curSymbolTable.parent;
        }
        return block;

    }

    private TreeNode BlockItem(TreeNode treeNode){
        TreeNode blockItem = new TreeNode();
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
        stmt.type = "Stmt";
        stmt.fnode = treeNode;
        treeNode.childNodes.add(stmt);

        if(isPrintf()){
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
            //处理f类错误(先到上一级取查找当前函数信息)
            String curFuncName = curSymbolTable.funcName;
            Symbol curFunc = curSymbolTable.parent.lookupSymbol(curFuncName,words.get(i).line_num);//行数不重要

            PrintWord(i);i++;//return
            if(!ReadWord(i).equals(";")){
                if(curFunc.type.equals("void")){
                    errors.append(words.get(i-1).line_num).append(" f\n");//如果是void函数不应该有exp
                    sa_errors.addError(words.get(i-1).line_num,"f");
                }
                Exp(stmt);
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
            }else{
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
        cond.type = "Cond";
        cond.fnode = treeNode;
        treeNode.childNodes.add(cond);
        LOrExp(cond);
        cond.print_node(ans);
        return cond;
    }
    private TreeNode LOrExp(TreeNode treeNode){
        //左递归
        TreeNode lOrExp_ = new TreeNode();
        lOrExp_.type = "LOrExp";
        LAndExp(lOrExp_);
        lOrExp_.print_node(ans);
        while(ReadWord(i).equals("||")){
            PrintWord(i++);// ||

            TreeNode lOrExp__ = new TreeNode();
            lOrExp_.fnode = lOrExp__;
            lOrExp__.childNodes.add(lOrExp_);
            lOrExp__.type = "LOrExp";
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
        lAndExp.type = "LAndExp";
        EqExp(lAndExp);
        lAndExp.print_node(ans);
        while(ReadWord(i).equals("&&")){
            PrintWord(i++);// &&

            TreeNode lAndExp_ = new TreeNode();
            lAndExp.fnode = lAndExp_;
            lAndExp_.childNodes.add(lAndExp);
            lAndExp_.type = "LAndExp";
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
        eqExp.type = "EqExp";

        RelExp(eqExp);
        eqExp.print_node(ans);
        while(ReadWord(i).equals("==")||ReadWord(i).equals("!=")){
            PrintWord(i++);// ==|!=

            TreeNode eqExp_ = new TreeNode();
            eqExp.fnode = eqExp_;
            eqExp_.childNodes.add(eqExp);
            eqExp_.type = "EqExp";
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
        relExp.type = "RelExp";

        AddExp(relExp);
        relExp.print_node(ans);
        while(ReadWord(i).equals(">")||ReadWord(i).equals("<")||ReadWord(i).equals(">=")||ReadWord(i).equals("<=")){
            PrintWord(i++); // >|<=

            TreeNode relExp_ = new TreeNode();
            relExp.fnode = relExp_;
            relExp_.childNodes.add(relExp);
            relExp_.type = "RelExp";
            AddExp(relExp_);
            relExp_.print_node(ans);
            relExp = relExp_;
        }
        relExp.fnode = treeNode;
        treeNode.childNodes.add(relExp);
//        relExp.print_node(ans);
        return relExp;
    }

    private TreeNode Exp(TreeNode treeNode){
        TreeNode exp = new TreeNode();
        exp.type = "Exp";
        exp.fnode = treeNode;
        treeNode.childNodes.add(exp);
        TreeNode addexp = AddExp(exp);
        exp.params = addexp.params;
        exp.print_node(ans);
        return exp;
    }
    private TreeNode AddExp(TreeNode treeNode){
        //左递归文法的处理
        TreeNode addExp_ = new TreeNode();
        addExp_.type = "AddExp";
        TreeNode mulExp = MulExp(addExp_);
        addExp_.print_node(ans);
        while(ReadWord(i).equals("+")||ReadWord(i).equals("-")){
            PrintWord(i++);// +|-

            //维护addExp_始终是最顶层的节点
            TreeNode addExp__ = new TreeNode();
            addExp__.type = "AddExp";
            addExp__.childNodes.add(addExp_);
            addExp_.fnode = addExp__;
            MulExp(addExp__);
            addExp__.print_node(ans);
            addExp_ = addExp__;
        }

        addExp_.fnode = treeNode;
        treeNode.childNodes.add(addExp_);
        addExp_.params = mulExp.params;
//        addExp_.print_node(ans);
        return addExp_;
    }
    private TreeNode MulExp(TreeNode treeNode){
        TreeNode mulExp = new TreeNode();
        mulExp.type = "MulExp";
        TreeNode unaryExp = UnaryExp(mulExp);
        mulExp.print_node(ans);
        while (ReadWord(i).equals("*")||ReadWord(i).equals("/")||ReadWord(i).equals("%")){
            PrintWord(i++);// *|/|%
            //维护mulExp最顶层节点
            TreeNode mulExp_ = new TreeNode();
            mulExp_.type = "MulExp";
            mulExp_.childNodes.add(mulExp);
            mulExp.fnode = mulExp_;
            UnaryExp(mulExp_);
            mulExp_.print_node(ans);
            mulExp = mulExp_;
        }

        mulExp.fnode = treeNode;
        treeNode.childNodes.add(mulExp);
        mulExp.params = unaryExp.params;
//        mulExp.print_node(ans);
        return mulExp;
    }

    private TreeNode UnaryExp(TreeNode treeNode){
        TreeNode unaryExp = new TreeNode();
        unaryExp.fnode = treeNode;
        treeNode.childNodes.add(unaryExp);
        unaryExp.type = "UnaryExp";

        if(ReadWord(i).equals("+")||ReadWord(i).equals("-")||ReadWord(i).equals("!")){
            UnaryOp(unaryExp);
            TreeNode unaryExp_new = UnaryExp(unaryExp);
            unaryExp.params = unaryExp_new.params;
        }else if((!ReadWord(i).equals("("))&&ReadWord(i+1).equals("(")){
            //处理c类错误
            Symbol curFunc = null;
            String name = ReadWord(i);
            int use_line = words.get(i).line_num;
            if(curSymbolTable.lookupSymbol(name,use_line)==null){
                errors.append(use_line).append(" c\n");
                sa_errors.addError(use_line,"c");


            }else{
                curFunc = curSymbolTable.lookupSymbol(name,0);
            }

            PrintWord(i++);//ident
            PrintWord(i++);// (

            if(!ReadWord(i).equals(")")){
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
            TreeNode primaryExp = PrimaryExp(unaryExp);
            unaryExp.params = primaryExp.params;
        }

        unaryExp.print_node(ans);
        return unaryExp;
    }

    private TreeNode UnaryOp(TreeNode treeNode) {
        TreeNode unaryOp = new TreeNode();
        unaryOp.fnode = treeNode;
        treeNode.childNodes.add(unaryOp);
        unaryOp.type = "UnaryOp";

        PrintWord(i++);// +|-|!
        unaryOp.print_node(ans);
        return unaryOp;
    }

    private TreeNode PrimaryExp(TreeNode treeNode){
        TreeNode primaryExp = new TreeNode();
        primaryExp.fnode = treeNode;
        treeNode.childNodes.add(primaryExp);
        primaryExp.type = "PrimaryExp";
        if(ReadWord(i).equals("(")){
            PrintWord(i++);// (
            Exp(treeNode);
            PrintWord(i++);// )
        }else if(ReadType(i).equals("INTCON")){
            Number(treeNode);
        }else{
            LVal(treeNode);
        }
        primaryExp.print_node(ans);
        return primaryExp;
    }

    private TreeNode LVal(TreeNode treeNode){
        TreeNode lVal = new TreeNode();
        lVal.fnode = treeNode;
        treeNode.childNodes.add(lVal);
        lVal.type = "LVal";
        lVal.content = ReadWord(i);//储存ident的内容 用于查表
        lVal.line = words.get(i).line_num;//储存ident使用行数

        //处理c类错误
        if(curSymbolTable.lookupSymbol(lVal.content,lVal.line) == null){
            errors.append(lVal.line).append(" c\n");
            sa_errors.addError(lVal.line,"c");

        }

        PrintWord(i++);//ident
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
        lVal.dimension = dimension;
        return lVal;
    }
    private TreeNode Number(TreeNode treeNode){
        TreeNode number = new TreeNode();
        number.type = "Number";
        number.fnode = treeNode;
        treeNode.childNodes.add(number);
        PrintWord(i++);//intconst
        number.print_node(ans);
        return number;
    }
    private TreeNode FuncRParams(TreeNode treeNode){
        TreeNode funcRParams = new TreeNode();
        funcRParams.type = "FuncRParams";
        funcRParams.fnode = treeNode;
        treeNode.childNodes.add(funcRParams);

        TreeNode exp = Exp(funcRParams);
        int j = i;
        if(ReadWord(j).equals(")")){
            while(!ReadWord(j).equals("("))
                j--;
        }
        if(curSymbolTable.lookupSymbol(ReadWord(j-1),0) != null){
            Symbol this_symbol = curSymbolTable.lookupSymbol(ReadWord(j-1),0);
            if(this_symbol.type.equals("void")){
                funcRParams.params.add(new Param(-1));
            }else{
                funcRParams.params.add(new Param(this_symbol.dimension));
            }
        }else{
            funcRParams.params.add(new Param(exp.dimension));
        }

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
            j = i;
            if(ReadWord(j).equals(")")){
                while(!ReadWord(j).equals("("))
                    j--;
            }
            if(curSymbolTable.lookupSymbol(ReadWord(j-1),0) != null){
                Symbol this_symbol = curSymbolTable.lookupSymbol(ReadWord(j-1),0);
                if(this_symbol.type.equals("void")){
                    funcRParams.params.add(new Param(-1));
                }else{
                    funcRParams.params.add(new Param(this_symbol.dimension));
                }
            }else{
                funcRParams.params.add(new Param(exp_.dimension));
            }
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
}

class TreeNode {
    TreeNode fnode = null;
    List<TreeNode> childNodes = new ArrayList<>();
    String type = "";
    String content = "";
    int line = 0;
    List<Param> params = new ArrayList<>();//对于函数来说它的形参个数和维度记录
    int dimension;//对LVal来说变量的维度

    public void print_node(StringBuilder ans){
//        System.out.println("<" + type + ">");
        String str = "<" + type + ">" + '\n';
        ans.append(str);
    }
}



