import 'package:aphasia_recovery/mixin/widgets_mixin.dart';
import 'package:aphasia_recovery/states/user_identity.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'patient/home.dart';

class RegisterPage extends StatefulWidget {
  final CommonStyles? commonStyles;

  const RegisterPage({
    super.key,
    this.commonStyles,
  });

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage>
    with StateWithTextFields, TextFieldCommonValidators {
  // 添加样式常量
  static const _inputBorderRadius = 8.0;
  static const _buttonPadding =
      EdgeInsets.symmetric(vertical: 12, horizontal: 24);
  static const _cardElevation = 4.0;
  final Map<String, dynamic> registerInfo = {};

  bool _isLoading = false;

  @override
  void initFieldSettings() {
    fieldsSetting['identity'] = FieldSetting(
      key: GlobalKey<FormFieldState>(debugLabel: "registerPhoneOrEmail"),
      ctrl: TextEditingController(),
      validator: (value) {
        String? errMsg = notEmptyValidator("手机/邮箱")(value);

        if (errMsg == null) {
          String val = value!;
          String emailPattern = r'^[\w-]+(\.[\w-]+)*@([\w-]+\.)+[a-zA-Z]{2,7}$';
          RegExp regExp = RegExp(emailPattern);

          RegExp phoneRegex = RegExp(r'^1[3-9]\d{9}$');
          if (!regExp.hasMatch(val) && !phoneRegex.hasMatch(val)) {
            errMsg = "请输入邮箱或手机号";
          }
        }

        return errMsg;
      },
      reset: () => fieldsSetting['identity']!.ctrl.text = "",
      applyToModel: () =>
          registerInfo['identity'] = fieldsSetting['identity']!.ctrl.text,
    );

    fieldsSetting['password'] = FieldSetting(
      key: GlobalKey<FormFieldState>(debugLabel: "registerPassword"),
      ctrl: TextEditingController(),
      validator: (value) {
        value = value ?? "";
        String? errMsg = notEmptyValidator("密码")(value);

        if (errMsg == null && (value.length > 15 || value.length < 7)) {
          errMsg = "请设置长度为7-15的密码，当前长度${value.length}";
        }

        return errMsg;
      },
      reset: () => fieldsSetting['password']!.ctrl.text = "",
      applyToModel: () =>
          registerInfo['password'] = fieldsSetting['password']!.ctrl.text,
    );

    fieldsSetting['secondPassword'] = FieldSetting(
      key: GlobalKey<FormFieldState>(debugLabel: "registerPhoneOrEmail"),
      ctrl: TextEditingController(),
      validator: (value) {
        String? errMsg = notEmptyValidator("确认密码")(value);

        if (errMsg == null &&
            fieldsSetting['secondPassword']?.ctrl.text !=
                fieldsSetting['password']?.ctrl.text) {
          return "两次输入的密码不一致";
        }

        return errMsg;
      },
      reset: () => fieldsSetting['secondPassword']!.ctrl.text = "",
      applyToModel: () => registerInfo['secondPassword'] =
          fieldsSetting['secondPassword']!.ctrl.text,
    );
  }

  @override
  void initState() {
    initFieldSettings();

    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    CommonStyles? commonStyles = widget.commonStyles;

    return Scaffold(
      appBar: AppBar(
        title: Text("用户注册", style: commonStyles?.titleStyle),
        elevation: 0,
      ),
      body: SafeArea(
        child: Container(
          color: Theme.of(context).colorScheme.primaryContainer,
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(24.0),
              child: Container(
                constraints: const BoxConstraints(maxWidth: 600),
                child: Card(
                  elevation: _cardElevation,
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: SingleChildScrollView(
                      child: Form(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Text(
                              "公开注册仅创建患者账号。医疗人员账号请联系系统管理员开通。",
                              style: commonStyles?.bodyStyle,
                            ),
                            const SizedBox(height: 24),
                            _buildInputField(
                              label: '手机号/邮箱',
                              fieldKey: fieldsSetting['identity']!.key,
                              controller: fieldsSetting['identity']!.ctrl,
                              validator: fieldsSetting['identity']!.validator,
                            ),
                            const SizedBox(height: 16),
                            _buildPasswordField(
                              label: '密码',
                              controller: fieldsSetting['password']!.ctrl,
                              validator: fieldsSetting['password']!.validator,
                            ),
                            const SizedBox(height: 16),
                            _buildPasswordField(
                              label: '确认密码',
                              controller: fieldsSetting['secondPassword']!.ctrl,
                              validator:
                                  fieldsSetting['secondPassword']!.validator,
                            ),
                            const SizedBox(height: 32),
                            _buildRegisterButton(commonStyles),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  // 新增通用输入框构建方法
  Widget _buildInputField({
    required String label,
    required GlobalKey<FormFieldState> fieldKey,
    required TextEditingController controller,
    required FormFieldValidator<String> validator,
  }) {
    return TextFormField(
      key: fieldKey,
      controller: controller,
      decoration: InputDecoration(
        labelText: label,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(_inputBorderRadius),
        ),
        prefixIcon: const Icon(Icons.person_outline),
      ),
      validator: validator,
      autovalidateMode: AutovalidateMode.onUserInteraction,
    );
  }

  // 密码输入框组件
  Widget _buildPasswordField({
    required String label,
    required TextEditingController controller,
    required FormFieldValidator<String> validator,
  }) {
    return TextFormField(
      controller: controller,
      obscureText: true,
      decoration: InputDecoration(
        labelText: label,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(_inputBorderRadius),
        ),
        prefixIcon: const Icon(Icons.lock_outline),
      ),
      validator: validator,
      autovalidateMode: AutovalidateMode.onUserInteraction,
    );
  }

  // 注册按钮组件
  Widget _buildRegisterButton(CommonStyles? commonStyles) {
    return ElevatedButton.icon(
      icon: _isLoading
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2))
          : const Icon(Icons.app_registration),
      label: Text(_isLoading ? "注册中..." : "立即注册"),
      style: ElevatedButton.styleFrom(
        padding: _buttonPadding,
        shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(_inputBorderRadius)),
      ),
      onPressed: _isLoading
          ? null
          : () async {
              setState(() => _isLoading = true);
              try {
                await _handleRegister(widget.commonStyles);
              } finally {
                if (mounted) {
                  setState(() => _isLoading = false);
                }
              }
            },
    );
  }

  // 优化后的注册处理逻辑
  Future<void> _handleRegister(CommonStyles? commonStyles) async {
    if (!applyFieldsChangesToModel()) return;

    final payload = <String, dynamic>{
      'identity': registerInfo['identity'],
      'password': registerInfo['password'],
      'role': 1,
    };

    try {
      final userIdentity = await UserIdentity.register(payload);
      if (!mounted) return;
      _navigateToHome(userIdentity, commonStyles);
    } on AuthBusinessException catch (e) {
      if (!mounted) return;
      showErrorToast(context, e.message, commonStyles);
    } catch (e) {
      if (!mounted) return;
      showErrorToast(context, "注册失败，请检查网络后重试", commonStyles);
    }
  }

  // 新增错误提示方法
  void showErrorToast(
      BuildContext context, String msg, CommonStyles? commonStyles) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg, style: commonStyles?.bodyStyle),
        backgroundColor: commonStyles?.errorColor ?? Colors.red[700],
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  // 新增导航方法
  void _navigateToHome(UserIdentity identity, CommonStyles? commonStyles) {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (context) => ChangeNotifierProvider.value(
          value: identity,
          child: HomePage(commonStyles: commonStyles),
        ),
      ),
    );
  }
}
