export namespace MMF {
    export type ContactType = 'friend' | 'group' | 'bot' | 'member';


    export interface Contact {
        readonly contactId: string;
        displayName: string;
        readonly nativeId: number;
        readonly type: ContactType;

        contactInfo: ContactInfo;
        friendInfo?: FriendInfo;
        groupInfo?: GroupInfo;

        subMessages: Messaging.MessageBase[];
        lastSpeakTimestamp: number;
        bindDom?: {
            card: HTMLDivElement;
            name: HTMLSpanElement;
            time: HTMLSpanElement;
            msgLine: HTMLDivElement;
        };
    }

    export interface ContactInfo {
        readonly type: ContactType;
        displayName: string;
        name: string;
        contactId: string; // Not available for members


        readonly nativeId: number;
    }

    export interface BotInfo extends ContactInfo {
    }

    export interface FriendInfo extends ContactInfo {
        lastSpeakTimestamp: number;
        name: string;
        nick: string;
    }

    export interface GroupInfo extends ContactInfo {
        memberCount: number;
        members: { [nativeId: number]: MemberInfo };
        ownerId: number;
        owner: MemberInfo;
    }

    /** MEMBER, ADMIN, OWNER */
    export type MemberPermission = 0 | 1 | 2;

    export interface MemberInfo extends ContactInfo {
        lastSpeakTimestamp: number;
        permission: MemberPermission;
        nameCard: string;
        chatColor: string;
        declaredGroupId: string;
    }

    export namespace MiraiMsg {
        export type MsgType = 'plain' | '@' | 'reply' | 'image' | 'flashImage' | 'forward';

        export interface Message {
            type: MsgType;
        }

        export interface Plain extends Message {
            content: string;
        }

        export interface At extends Message {
            target: number;
        }

        export interface Image extends Message {
            imgId: string;
            url: string;
            internalData: string;
        }

        export interface FlashImage extends Image {
        }

        export interface Forward extends Message {
            preview: string[];
            title: string;
            brief: string;
            source: string;
            summary: string;
            nodeList: ForwardNode[];

        }

        export interface ForwardNode {
            senderNativeId: number;
            timestamp: number;
            senderName: string;
            msg: MessageChain
        }

        export interface Reply {
            srcIds: number[];
            srcInternalIds: number[];
            srcTimestamp: number;
            srcMsgSourceSnapshot: string;
        }

        export type MessageChain = MiraiMsg.Message[];
    }

    export namespace Messaging {
        export type MsgType = 'system' | 'normal';

        export interface MessageBase {
            readonly type: MsgType;

            readonly messageId: string;
            readonly timestamp: number;

            readonly fedit$inode?: ForwardEditor.INode;
        }

        export interface SystemMessage extends MessageBase {
            content: string;
        }

        export interface NormalMessage extends MessageBase {
            ids: number[];
            internalIds: number[];

            sender: ContactInfo;

            msg: MiraiMsg.MessageChain;
        }
    }

    export interface SingleMessageEditor {
        window: Window;
        postMessage: (msg: any) => void;
        available: boolean;
    }


    export namespace ForwardEditor {
        export interface INode {
            message: MiraiMsg.MessageChain
            senderName: string
            sender: number
            timestamp: number
        }
    }
}