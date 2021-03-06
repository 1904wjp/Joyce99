package com.moon.joyce.example.controller;



import com.moon.joyce.commons.base.cotroller.BaseController;
import com.moon.joyce.commons.constants.Constant;
import com.moon.joyce.commons.utils.*;
import com.moon.joyce.example.entity.vo.PageVo;
import com.moon.joyce.example.functionality.entity.Result;
import com.moon.joyce.example.entity.User;
import com.moon.joyce.example.functionality.entity.VerifyCode;
import com.moon.joyce.example.functionality.server.WebSocket;
import com.moon.joyce.example.functionality.service.FileService;
import com.moon.joyce.example.service.UserService;
import com.moon.joyce.example.service.serviceControllerDetails.UserServiceControllerDetail;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * <p>
 *  用户前端控制器
 * </p>
 *
 * @author Joyce
 * @since 2021-09-01
 */
@Controller
@RequestMapping("/example/user")
public class UserController extends BaseController {


/*********************************************************************************************************************************************/
/**************全局变量***********************/
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private UserService userService;

    /**
     * 页面路径前缀
     */
    private final String  pagePrefix = "user/";

    /**
     * url的路径前缀
     */
    private final String urlPrefix = "/example/user/";
    @Autowired
    private FileService fileService;
    /**
     * 文件路径
     */
    /*@Value("${file.upload.path}")
    private  String filePah;*/

    @Autowired
    private UserServiceControllerDetail userServiceControllerDetail;

    /**
     * 聊天服务
     */
    @Autowired
    private WebSocket webSocket;

    /*****************************************************************************************************************************************************************/
    /***********页面映射****************/

    /**
     * bar页面
     */
    @GetMapping("/headerBar")
    public String getHeaderBarPage(){
        return "common/public/headerBar.html";
    }
    /**
     * user列表页面
     */
    @GetMapping("/userList")
    public String getUsersPage(){
        return pagePrefix+"userListPage";
    }

    /**
     * 注册页面
     * @return
     */
    @GetMapping("/regist")
    public String registPage(){
        return pagePrefix+"registPage";
    }

    /**
     * 注册结果页面
     * @return
     */
    @GetMapping("/statusResult")
    public String statusResultPage(){
        return pagePrefix+"statusResultPage";
    }

    /**
     * 登录页面
     * @return
     */
    @GetMapping("/login")
    public String loginPage(){
        return pagePrefix+"loginPage";
    }

    /**
     * 查看页面
     * @param id
     * @param map
     * @return
     */
    @GetMapping("/queryUser/{id}")
    public String queryUserPage(@PathVariable("id") Long id,ModelMap map){

        User dbUser = userService.getById(id);
        map.addAttribute("user",dbUser);
        return pagePrefix+"queryUserPage";
    }

    /**
     * 编辑页面
     * @param id
     * @param map
     * @return
     */
    @GetMapping("/editUser/{id}")
    public String updateUserPage(@PathVariable Long id,ModelMap map){
     if (!id.equals( getSessionUser().getId())){
            return pagePrefix+"error";
        }
        User dbUser = userService.getById(id);
        if (Objects.nonNull(dbUser)){
            map.addAttribute("user",dbUser);
            return pagePrefix+"updateUserPage";
        }
        return pagePrefix+"userListPage";
    }

    /**
     * 聊天页面
     * @return
     */
    @RequestMapping("/websocket")
    public String indexPage(ModelMap map){
        User sessionUser = getSessionUser();
        List<User> userList = userService.getUserList(null);
        userList.remove(getSessionUser());
        map.put("users",userList);
        map.addAttribute("user",sessionUser);
        map.addAttribute("websocketUrlPrefix",appUrlValue+"/websocket");
        return pagePrefix+"websocketPage";
    }

    /**************************************************************************************************************************************************************************************************************/
    /**********逻辑判断**********/
    /**
     * user数据保存方法
     * @param user
     * @return
     */
    @ResponseBody
    @RequestMapping("/doSaveUser")
    public Result saveUser(User user){
        //创建保存数据结果
        boolean result = false;
        //密码加密
        if (!StringUtils.isBlank(user.getPassword())){
            user.setPassword(MD5Utils.getMD5Str(user.getPassword()));
        }
        if (Objects.nonNull(user.getId())){
            user.setUpdateTime(new Date());
            result = userService.saveOrUpdate(user);
        }
        //注册
        if (Objects.isNull(user.getId())){
            user.setStatus(0);
            //非空判断
            int userCountByUsername = userService.getUserCount(user, Constant.USER_TYPE_UNIQUE_USERNAME);
            if (userCountByUsername== Constant.RESULT_UNKNOWN_SQL_RESULT){
                return ResultUtils.error(Constant.ERROR_FILL_ERROR_CODE);
            }
            //用户是否唯一
            if (userCountByUsername>Constant.RESULT_NO_SQL_RESULT){
                return ResultUtils.error(Constant.ERROR_CODE,Constant.CHINESE_SLECET_EXIST_USERNAME_MESSAGE);
            }
            //非空判断
            int userCountByEmail = userService.getUserCount(user, Constant.USER_TYPE_UNIQUE_EMAIL);
            if (userCountByEmail== Constant.RESULT_UNKNOWN_SQL_RESULT){
                return ResultUtils.error(Constant.ERROR_FILL_ERROR_CODE);
            }
            //邮件是否唯一
            if (userCountByEmail>Constant.RESULT_NO_SQL_RESULT){
                return ResultUtils.error(Constant.ERROR_CODE,Constant.CHINESE_SLECET_EXIST_EMAIL_MESSAGE);
            }
            user.setStatus(1);
            user.setDeleteFlag(0);
            //发送邮件
         /*   userServiceControllerDetail.getEmailAddress(user,appUrl);*/
            user.setFileUrl(Constant.FILE_DEFAULT_NAME);
            user.setCreateTime(new Date());
            result =  userService.saveOrUpdate(user);
        }
        //结果处理
        if (result){
            User sessionUser = getSessionUser();
            if (null!=sessionUser){
               removeSessionUser();
            }
            setSession(Constant.SESSION_USER,user);
            //加载配置文件
            fileService.writeJoyceConfig(user.getUsername(),null);
            return ResultUtils.success();
        }
            return  ResultUtils.error(Constant.ERROR_CODE,false);
    }

    /**
     * 邮政验证
     * @param code
     * @returnc
     */
    @RequestMapping("/checkCode")
    public String checkCode(String code){
        User user = new User();
        user.setStatusCode(code);
        if (Constant.RESULT_ONE_SUCCESS_SQL_RESULT!=userService.getUserCount(user,Constant.USER_TYPE_UNIQUE_STATUS_CODE)){
            return Constant.REDIRECT+urlPrefix+"regist";
        }
        User dbUser = userService.getUser(user,Constant.USER_TYPE_UNIQUE_STATUS_CODE);
        if (Objects.nonNull(dbUser)){
            //激活成功，让之前生成code失效
            String md5Code = MD5Utils.getMD5Str(code);
            dbUser.setStatusCode(md5Code);
            User updateUser= new User();
            updateUser.setId(dbUser.getId());
            userService.updateUser(dbUser,updateUser,Constant.USER_TYPE_UP_VAILD_STATUS);
        }
        return Constant.REDIRECT+urlPrefix+"login";
    }

    /**
     *  登录验证
     * @param username
     * @param password
     * @return
     */
    @ResponseBody
    @RequestMapping("/doLogin")
    public Result loginUser(@RequestParam("username") String username,@RequestParam("password") String password){
        User user = new User();
        user.setUsername(username);
        user.setPassword(MD5Utils.getMD5Str(password));
        User dbUser = userService.getUser(user, Constant.USER_TYPE_LOGIN);
        if (Objects.nonNull(dbUser)){
            if(!user.getPassword().equals(dbUser.getPassword())){
              return ResultUtils.error(Constant.CHINESE_PASSWORD_ERROR_MESSAGE);
            }
            //头像为空则为默认值
            if (StringUtils.isEmpty(dbUser.getFileUrl())){
                dbUser.setFileUrl(Constant.FILE_DEFAULT_NAME);
            }

            setSession(Constant.SESSION_USER,dbUser);
            return ResultUtils.success();
        }
        return ResultUtils.error(Constant.ERROR_CODE,Constant.CHINESE_SLECET_BLANK_USERNAME_MESSAGE);
    }

    /**
     * 获取所有用户数据
     * @param user
     * @return
     */
    @ResponseBody
    @RequestMapping("/userListData")
    @Transactional
    public PageVo getUsers(User user){
        //得到总页数
        int totle = userService.getUsersCount();
        //得到user数据对象
        List<User> rows = userService.getUserList(user);
        return  new PageVo(rows,totle);
    }

    /**
     * 删除某个user
     * @return
     */
    @ResponseBody
    @RequestMapping("/deleteUser")
    public Result deleteUser(@RequestParam String ids){
        if (StringUtils.isBlank(ids)){
            return ResultUtils.error(Constant.NULL_CODE);
        }
        List<String> list = com.moon.joyce.commons.utils.StringUtils.StrToList(ids);
        boolean del = userService.removeByIds(list);
        if (del){
            if (com.moon.joyce.commons.utils.StringUtils.listIsContainsStr(getSessionUser().getId().toString(),list)){
                return ResultUtils.error(Constant.NULL_CODE);
            }
            return ResultUtils.success();
        }
        return ResultUtils.error(Constant.ERROR_CODE);
    }


    /**
     * 冻结某个user
     * @return
     */
    @ResponseBody
    @RequestMapping("/freezeUser")
    public Result freezeUser(@RequestParam String ids){
        if (StringUtils.isBlank(ids)){
            return ResultUtils.error(Constant.NULL_CODE);
        }
        User dbUser = userService.getById(Long.valueOf(ids));
        if (Objects.isNull(dbUser)){
            return ResultUtils.error(Constant.NULL_CODE);
        }
        dbUser.setStatus(Constant.USER_TYPE_FROZEN_STATUS);
        boolean update = userService.updateById(dbUser);
        if (update){
            return ResultUtils.success();
        }
        return ResultUtils.error(Constant.ERROR_CODE);
    }

    /**
     * 恢复某个冻结的user
     * @return
     */
    @ResponseBody
    @RequestMapping("/recoverUser")
    public Result recoverUser(@RequestParam String ids){
        if (StringUtils.isBlank(ids)){
            return ResultUtils.error(Constant.NULL_CODE);
        }
        User dbUser = userService.getById(Long.valueOf(ids));
        if (Objects.isNull(dbUser)){
            return ResultUtils.error(Constant.NULL_CODE);
        }
        dbUser.setStatus(Constant.USER_TYPE_VAILD_STATUS);
        boolean update = userService.updateById(dbUser);
        if (update){
            return ResultUtils.success();
        }
        return ResultUtils.error(Constant.ERROR_CODE);
    }

    /**
     * 编辑user的数据
     * @param id
     * @return
     */
    @ResponseBody
    @RequestMapping("/doQueryUser")
    public Result updateUser(@RequestParam String id){
        User dbUser = userService.getById(Long.valueOf(id));
        if (Objects.nonNull(dbUser)){
            if (Constant.USER_TYPE_INVAILD_STATUS.equals(dbUser.getStatus())){
                return ResultUtils.error(Constant.INACTIVE_CODE);
            }
            if (Constant.USER_TYPE_FROZEN_STATUS.equals(dbUser.getStatus())){
                return ResultUtils.error(Constant.FROZEN_CODE);
            }
            if (Constant.USER_TYPE_VAILD_STATUS.equals(dbUser.getStatus())){
                return ResultUtils.success(dbUser);
            }
        }
        return ResultUtils.error(Constant.NULL_CODE);
    }

    /**
     * 忘记密码
     * @return
     */
    @ResponseBody
    @RequestMapping("/getEmailCode")
    public Result getEmailCode(@RequestParam String email){
        boolean result = false;
        String mailCode = null;
        User user = new User();
        user.setEmail(email);
        User dbUser = userService.getUser(user, "");
        if (Objects.isNull(dbUser)){
            return ResultUtils.error(Constant.NULL_CODE);
        }
        VerifyCode sessionVerifyCode= (VerifyCode) getSessionValue(Constant.SESSION_VERIFY_CODE+dbUser.getId());
        if (Objects.isNull(sessionVerifyCode)){
            mailCode = EmailUtils.SendMailCode(user.getEmail(), 6);
            VerifyCode verifyCode = new VerifyCode();
            verifyCode.setCreateTime(new Date());
            verifyCode.setVerifyCodeValue(mailCode);
            verifyCode.setVaildTime(6*10000L);
            setSession(Constant.SESSION_VERIFY_CODE+dbUser.getId(),verifyCode);
            return  ResultUtils.success(Constant.SEND_EMAIL_SEND_SUCCESS_MESSAGE);
        }else {
            //是否超过验证码有效时间
            result = DateUtils.dateCompare(sessionVerifyCode.getCreateTime(), new Date(), sessionVerifyCode.getVaildTime());
        }
        if (result){
            //超过了有效时间，移除之前的验证码，可重新发送验证码
            removeSessionValue(Constant.SESSION_VERIFY_CODE+dbUser.getId());
            getSession().removeAttribute(Constant.SESSION_VERIFY_CODE+dbUser.getId());
            mailCode = EmailUtils.SendMailCode(user.getEmail(), 6);
            VerifyCode verifyCode = new VerifyCode();
            verifyCode.setCreateTime(new Date());
            verifyCode.setVerifyCodeValue(mailCode);
            verifyCode.setVaildTime(6*10000L);
            getSession().setAttribute(Constant.SESSION_VERIFY_CODE+dbUser.getId(),verifyCode);
           return ResultUtils.success(Constant.SEND_EMAIL_SEND_SUCCESS_MESSAGE);
        }else {
            //有效时间不能重复发送验证码
           return ResultUtils.error(Constant.ERROR_CODE,Constant.SEND_EMAIL_SEND_VAILD_TIME_MESSAGE);
        }
    }

    /**
     * 检查验证码修改密码验证
     * @param emailCode
     * @param email
     * @param newPassword
     * @return
     */
    @ResponseBody
    @RequestMapping("/forgetPassword")
    public Result checkUpdateCode(@RequestParam String emailCode,@RequestParam String email,@RequestParam String newPassword){
        User user = new User();
        user.setEmail(email);
        User dbUser = userService.getUser(user, "");
        if (Objects.isNull(dbUser)){
            return ResultUtils.error("该邮件未注册");
        }
        VerifyCode verifyCode = (VerifyCode) getSessionValue(Constant.SESSION_VERIFY_CODE+dbUser.getId());
        boolean vaildTime = DateUtils.dateCompare(verifyCode.getCreateTime(), new Date(), verifyCode.getVaildTime());
        //判断验证码是否失效
        if (vaildTime){
            return ResultUtils.error("验证码已失效");
        }
        if (StringUtils.isBlank(emailCode)){
            return ResultUtils.error(Constant.ERROR_FILL_ERROR_CODE);
        }
        //判断填入数据是否正确
        if (verifyCode.getVerifyCodeValue().equals(emailCode)){
            getSession().removeAttribute(Constant.SESSION_VERIFY_CODE+dbUser);
            dbUser.setPassword(newPassword);
            boolean result = userService.updateById(dbUser);
            if (result) {
                return ResultUtils.success();
            }
        }
        return ResultUtils.error(Constant.ERROR_FILL_ERROR_CODE);
    }

    /**
     * 输入新密码
     * @param newPassword
     * @param password
     * @param id
     * @return
     */
    @ResponseBody
    @RequestMapping("/updatePassword")
    public Result updatePassword(@RequestParam("password") String password,@RequestParam("newPassword") String newPassword,@RequestParam("userId")Long id ){
      /*  User user = (User) request.getSession().getAttribute(Constant.SESSION_USER);*/
        if (StringUtils.isBlank(password.trim())){
            return ResultUtils.error(Constant.CHINESE_BLANK_MESSAGE);
        }
        if (StringUtils.isBlank(newPassword.trim())){
            return ResultUtils.error(Constant.CHINESE_BLANK_MESSAGE);
        }
        User user = userService.getById(id);
        if (!(user.getPassword().equals(MD5Utils.getMD5Str(password)))){
            return ResultUtils.error(Constant.ERROR_FILL_ERROR_CODE);
        }
        user.setPassword(MD5Utils.getMD5Str(newPassword));
        boolean updateById = userService.updateById(user);
        if (updateById){
            userService.updateById(user);
            removeSessionUser();
            return ResultUtils.success();
        }
        return ResultUtils.error(Constant.ERROR_CODE);
    }

    /**
     * 文件上传
     * @param file
     * @return
     */
    @ResponseBody
    @PostMapping("/upload")
    public Result uploadImg(@RequestParam("file") MultipartFile file){
        String filePath = fileService.uploadImg(file);
        return ResultUtils.success(Constant.RESULT_SUCCESS_MSG,filePath);
    }

    /**
     * 退出登录
     */
    @ResponseBody
    @PostMapping("/toRemoveUser")
    public Result toRemoveUser(){
        removeSessionUser();
        return ResultUtils.success();
    }

    /**
     * 用户发送数据
     * @param username
     * @param msg
     */
    @ResponseBody
    @GetMapping("/sendTo")
    public void sendTo(@RequestParam("username") String username,@RequestParam("msg") String msg) {
        webSocket.sendMessageTo(msg,username);
    }
}

