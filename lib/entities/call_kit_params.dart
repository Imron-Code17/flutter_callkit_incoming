import 'package:flutter_callkit_incoming/entities/notification_params.dart';
import 'package:json_annotation/json_annotation.dart';

import 'android_params.dart';
import 'ios_params.dart';

part 'call_kit_params.g.dart';

/// Object config for General.
@JsonSerializable(explicitToJson: true)
class CallKitParams {
  const CallKitParams({
    this.id,
    this.title,
    this.subtitle,
    this.senderName,
    this.senderMessage,
    this.appName,
    this.avatar,
    this.handle,
    this.type,
    this.normalHandle,
    this.duration,
    this.textFollowUp,
    this.textDecline,
    this.textLater,
    this.missedCallNotification,
    this.extra,
    this.headers,
    this.android,
    this.ios,
  });

  final String? id;
  final String? title;
  final String? subtitle;
  final String? senderName;
  final String? senderMessage;
  final String? appName;
  final String? avatar;
  final String? handle;
  final int? type;
  final int? normalHandle;
  final int? duration;
  final String? textFollowUp;
  final String? textDecline;
  final String? textLater;
  final NotificationParams? missedCallNotification;
  final Map<String, dynamic>? extra;
  final Map<String, dynamic>? headers;
  final AndroidParams? android;
  final IOSParams? ios;

  factory CallKitParams.fromJson(Map<String, dynamic> json) =>
      _$CallKitParamsFromJson(json);

  Map<String, dynamic> toJson() => _$CallKitParamsToJson(this);
}
