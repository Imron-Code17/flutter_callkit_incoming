// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'call_kit_params.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CallKitParams _$CallKitParamsFromJson(Map<String, dynamic> json) =>
    CallKitParams(
      id: json['id'] as String?,
      title: json['title'] as String?,
      subtitle: json['subtitle'] as String?,
      senderName: json['senderName'] as String?,
      senderMessage: json['senderMessage'] as String?,
      appName: json['appName'] as String?,
      avatar: json['avatar'] as String?,
      handle: json['handle'] as String?,
      type: (json['type'] as num?)?.toInt(),
      normalHandle: (json['normalHandle'] as num?)?.toInt(),
      duration: (json['duration'] as num?)?.toInt(),
      textFollowUp: json['textFollowUp'] as String?,
      textDecline: json['textDecline'] as String?,
      textLater: json['textLater'] as String?,
      missedCallNotification: json['missedCallNotification'] == null
          ? null
          : NotificationParams.fromJson(
              json['missedCallNotification'] as Map<String, dynamic>),
      extra: json['extra'] as Map<String, dynamic>?,
      headers: json['headers'] as Map<String, dynamic>?,
      android: json['android'] == null
          ? null
          : AndroidParams.fromJson(json['android'] as Map<String, dynamic>),
      ios: json['ios'] == null
          ? null
          : IOSParams.fromJson(json['ios'] as Map<String, dynamic>),
    );

Map<String, dynamic> _$CallKitParamsToJson(CallKitParams instance) =>
    <String, dynamic>{
      'id': instance.id,
      'title': instance.title,
      'subtitle': instance.subtitle,
      'senderName': instance.senderName,
      'senderMessage': instance.senderMessage,
      'appName': instance.appName,
      'avatar': instance.avatar,
      'handle': instance.handle,
      'type': instance.type,
      'normalHandle': instance.normalHandle,
      'duration': instance.duration,
      'textFollowUp': instance.textFollowUp,
      'textDecline': instance.textDecline,
      'textLater': instance.textLater,
      'missedCallNotification': instance.missedCallNotification?.toJson(),
      'extra': instance.extra,
      'headers': instance.headers,
      'android': instance.android?.toJson(),
      'ios': instance.ios?.toJson(),
    };
