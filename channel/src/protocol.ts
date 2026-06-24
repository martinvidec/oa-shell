// Zentrale Stelle für den Channel-Vertrag (NFA-06):
//  (a) claude ↔ channel  : offizielles Channels-Protokoll (MCP-Methodennamen)
//  (b) channel ↔ app      : Bridge-Envelope-Typen

// (a) Channels-Protokoll (Research Preview — bei Änderungen NUR hier anpassen)
export const CHANNEL_NOTIFICATION = 'notifications/claude/channel';
export const PERMISSION_REQUEST = 'notifications/claude/channel/permission_request';
export const PERMISSION_VERDICT = 'notifications/claude/channel/permission';

// (b) Bridge-Envelopes — App -> Channel
export type AppToChannel =
  | { type: 'chat'; text: string; chat_id?: string }
  | { type: 'permission_verdict'; request_id: string; behavior: 'allow' | 'deny' }
  | { type: 'file_tree'; requestId: string; path: string }
  | { type: 'file_content'; requestId: string; path: string };

// (b) Bridge-Envelopes — Channel -> App
export type ChannelToApp =
  | { type: 'hello'; cwd: string; cwdBasename: string; channelVersion: string }
  | { type: 'reply'; chat_id: string; text: string }
  | {
      type: 'permission_request';
      request_id: string;
      tool_name: string;
      description: string;
      input_preview: string;
    }
  | { type: 'file_tree_result'; requestId: string; [key: string]: unknown }
  | { type: 'file_content_result'; requestId: string; [key: string]: unknown };
