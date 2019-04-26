import { NativeModules, Platform, Alert } from 'react-native';

import { listeners } from './actions'

const RNCallKeepModule = NativeModules.RNCallKeep;
const isIOS = Platform.OS === 'ios';
const supportConnectionService = !isIOS && Platform.Version >= 23;

class RNCallKeep {

  constructor() {
    this._callkitEventHandlers = new Map();
  }


  addEventListener = (type, handler) => {
    const listener = listeners[type](handler);

    this._callkitEventHandlers.set(handler, listener);
  };

  removeEventListener = (type, handler) => {
    const listener = this._callkitEventHandlers.get(handler);
    if (!listener) {
      return;
    }

    listener.remove();
    this._callkitEventHandlers.delete(handler);
  };

  setup = async (options) => {
    if (!isIOS) {
      return this._setupAndroid(options.android);
    }

    return this._setupIOS(options.ios);
  };

  hasDefaultPhoneAccount = async (options) => {
    if (!isIOS) {
      return this._hasDefaultPhoneAccount(options);
    }

    return;
  };

  displayIncomingCall = (uuid, handle, localizedCallerName, handleType = 'number', hasVideo = false) => {
    if (!isIOS) {
      RNCallKeepModule.displayIncomingCall(uuid, handle, localizedCallerName);
      return;
    }

    RNCallKeepModule.displayIncomingCall(uuid, handle, handleType, hasVideo, localizedCallerName);
  };

  startCall = (uuid, handle, handleType = 'number', hasVideo = false, contactIdentifier) => {
    if (!isIOS) {
      RNCallKeepModule.startCall(uuid, handle, contactIdentifier);
      return;
    }

    RNCallKeepModule.startCall(uuid, handle, handleType, hasVideo, contactIdentifier);
  };

  reportConnectedOutgoingCallWithUUID = (uuid) => {
    RNCallKeepModule.reportConnectedOutgoingCallWithUUID(uuid);
  };

  endCall = (uuid) => {
    RNCallKeepModule.endCall(uuid);
  };

  endAllCalls = () => {
    isIOS ? RNCallKeepModule.endAllCalls() : RNCallKeepModule.endCall();
  };

  supportConnectionService = () => supportConnectionService;

  hasPhoneAccount = async () =>
    isIOS ? true : await RNCallKeepModule.hasPhoneAccount();

  setMutedCall = (uuid, muted) => {
     if (!isIOS) {
      // Can't mute on Android
      return;
    }

    RNCallKeepModule.setMutedCall(uuid, muted);
  };

  checkIfBusy = () =>
    Platform.OS === 'ios'
      ? RNCallKeepModule.checkIfBusy()
      : Promise.reject('RNCallKeep.checkIfBusy was called from unsupported OS');

  checkSpeaker = () =>
    Platform.OS === 'ios'
      ? RNCallKeepModule.checkSpeaker()
      : Promise.reject('RNCallKeep.checkSpeaker was called from unsupported OS');

  setAvailable = (state) => {
    if (isIOS) {
      return;
    }

    // Tell android that we are able to make outgoing calls
    RNCallKeepModule.setAvailable(state);
  };

  setCurrentCallActive = () => {
    if (isIOS) {
      return;
    }

    RNCallKeepModule.setCurrentCallActive();
  };

  _setupIOS = async (options) => new Promise((resolve, reject) => {
    if (!options.appName) {
      reject('RNCallKeep.setup: option "appName" is required');
    }
    if (typeof options.appName !== 'string') {
      reject('RNCallKeep.setup: option "appName" should be of type "string"');
    }

    resolve(RNCallKeepModule.setup(options));
  });

  _setupAndroid = async (options) => {
    const hasAccount = await RNCallKeepModule.checkPhoneAccountPermission();
    const shouldOpenAccounts = await this._alert(options, hasAccount);

    if (shouldOpenAccounts) {
      RNCallKeepModule.openPhoneAccounts();
    }
  };

  _hasDefaultPhoneAccount = async (options) => {
    const hasDefault = await RNCallKeepModule.checkDefaultPhoneAccount();
    const shouldOpenAccounts = await this._alert(options, hasDefault);

    if (shouldOpenAccounts) {
      RNCallKeepModule.openPhoneAccountSettings();
    }
  };

  _alert = async (options, condition) => new Promise((resolve, reject) => {
    if (!condition) {
      return resolve(false);
    }

    Alert.alert(
      options.alertTitle,
      options.alertDescription,
      [
        {
          text: options.cancelButton,
          onPress: reject,
          style: 'cancel',
        },
        { text: options.okButton,
          onPress: () => resolve(true)
        },
      ],
      { cancelable: true },
    );
  });

  backToForeground() {
    if (isIOS) {
      return;
    }

    NativeModules.RNCallKeep.backToForeground();
  }

  /*
  static holdCall(uuid, onHold) {
    RNCallKeepModule.setHeldCall(uuid, onHold);
  }
  */
}

export default new RNCallKeep();
