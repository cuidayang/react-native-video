import React, {Component} from 'react';
import {
  Dimensions,
  findNodeHandle,
  NativeModules,
  requireNativeComponent,
  StyleSheet,
  UIManager,
  View,
  Alert,
  ToastAndroid
} from 'react-native';
import PropTypes from 'prop-types';
import FloatingVideo from './FloatingVideo';
const defaultIndicatorSize = 16;
const {width: D_WIDTH} = Dimensions.get('window');

class Video extends Component {
  static propTypes = {
    showIndicator: PropTypes.bool,
    options: PropTypes.object,
    onComplete: PropTypes.func,
    onPrepared: PropTypes.func,
    onError: PropTypes.func,
    onInfo: PropTypes.func,
    onProgressUpdate: PropTypes.func,
    onLoadProgressUpdate: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.currentTime = 0;
    this.state = {
      indicatorLeft: null,
      indicatorTop: null,
      showIndicator: false,
    };
  }


  static getDerivedStateFromProps(props, state) {
    const {style, showIndicator} = props;
    let width, height;
    if (style) {
      width = style.width;
      height = style.height;
    }
    let videoWrapperWidth = width || D_WIDTH;
    let videoWrapperHeight = height || D_WIDTH * 0.7;
    const indicatorLeft = videoWrapperWidth / 2 - defaultIndicatorSize / 2;
    const indicatorTop = videoWrapperHeight / 2 - defaultIndicatorSize / 2;
    if (
      indicatorLeft !== state.indicatorLeft
      || indicatorTop !== state.indicatorTop
      || showIndicator !== state.showIndicator
    ) {
      return {
        indicatorLeft,
        indicatorTop,
        videoWrapperWidth,
        videoWrapperHeight,
        showIndicator,
      };
    }
    return null;
  }

  play = () => {
    console.log('play');
    UIManager.dispatchViewManagerCommand(
      findNodeHandle(this.ref),
      UIManager.getViewManagerConfig('RNEasyIjkplayerView').Commands.play,
      null,
    );
  };

  pause = () => {
    console.log('pause');
    UIManager.dispatchViewManagerCommand(
      findNodeHandle(this.ref),
      UIManager.getViewManagerConfig('RNEasyIjkplayerView').Commands.pause,
      null,
    );
  };

  stop = () => {
    console.log('pause');
    UIManager.dispatchViewManagerCommand(
      findNodeHandle(this.ref),
      UIManager.getViewManagerConfig('RNEasyIjkplayerView').Commands.stop,
      null,
    );
  };

  seek = (time) => {
    UIManager.dispatchViewManagerCommand(
      findNodeHandle(this.ref),
      UIManager.getViewManagerConfig('RNEasyIjkplayerView').Commands.seekTo,
      [time],
    );
  };

  setSpeed = (speed)=>{
    UIManager.dispatchViewManagerCommand(
      findNodeHandle(this.ref),
      UIManager.getViewManagerConfig('RNEasyIjkplayerView').Commands.setSpeed,
      [speed],
    );
  };

  playInWindow = ()=>{
    FloatingVideo.hasOverlayPermission().then(value => {
      if (value) {
        FloatingVideo.open({
          video: {
            url: this.props.options.url,
          },
          seek: this.currentTime
        });
      } else {
        Alert.alert(
            '提示',
            `悬浮窗播放视频需要再应用设置中开启悬浮窗权限，请开启权限`,
            [
              {text: '取消', onPress: () => console.log('Cancel Pressed'), style: 'cancel'},
              {
                text: '立即开启', onPress: () => {
                  FloatingVideo.requestOverlayPermission()
                      .then(() => {
                        FloatingVideo.open({
                          video: {
                            url: this.props.options.url,
                          },
                          seek: this.currentTime,
                        });
                      })
                      .catch(e => {
                        ToastAndroid.show('悬浮窗播放视频需要再应用设置中开启悬浮窗权限，请开启权限', ToastAndroid.SHORT)
                      });
                },
              },
            ],
            {cancelable: false},
        );
      }
    });
  };

  /**
   *
   * @param callback
   */
  getDuration = (callback) => {
    NativeModules.RNEasyIjkplayerView.getDuration(findNodeHandle(this.ref), callback);
  };

  getSize = (callback) => {
    NativeModules.RNEasyIjkplayerView.getSize(findNodeHandle(this.ref), callback);
  };

  _onProgressUpdate = ({nativeEvent: {progress}}) => {
    this.currentTime = progress;
    const {onProgressUpdate} = this.props;
    onProgressUpdate && onProgressUpdate(progress);
  };

  _onPrepared = (event) => {
    console.log('on prepared');
    const {onPrepared} = this.props;
    onPrepared && onPrepared(event);

    this.setState({showIndicator: false});
    this.getSize((err, size) => {
      if (!err) {
        const {videoWrapperHeight, videoWrapperWidth} = this.state;
        if (size.width <= size.height) { //宽度小于高度, 左右留黑边
          let videoWidth = size.width / size.height * videoWrapperHeight;
          this.setState({
            videoWidth,
            videoHeight: videoWrapperHeight,
            videoLeft: (videoWrapperWidth - videoWidth) / 2,
          });
        } else { //宽度大于高度, 上下留黑边
          let videoHeight = size.height / size.width * videoWrapperWidth;
          this.setState({
            videoHeight,
            videoWidth: videoWrapperWidth,
            videoTop: (videoWrapperHeight - videoHeight) / 2,
          });
        }
      }
    });
  };

  _onLoadProgressUpdate = ({nativeEvent: {loadProgress}}) => {
    console.log('on loadProgressUpdate:', loadProgress);
    const {onLoadProgressUpdate} = this.props;
    onLoadProgressUpdate && onLoadProgressUpdate(loadProgress);
  };

  _onInfo = ({nativeEvent: {info}}) => {
    console.log('on Info:', info);
    const {onInfo} = this.props;
    onInfo && onInfo(info);
  };

  _onError = ({nativeEvent: {error}}) => {
    console.log('on error:', error);
    const {onError} = this.props;
    onError && onError(error);
  };

  _onComplete = () => {
    const {onComplete} = this.props;
    onComplete && onComplete();
  };

  render() {

    return <View style={{
      position: 'absolute',
      left: 0,
      top: 0,
      right: 0,
      bottom: 0,
    }}>
      <IJKPlayer
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          right: 0,
          bottom: 0,
        }}
        {...this.props}

        ref={ref => this.ref = ref}
        onPrepared={this._onPrepared}
        onProgressUpdate={this._onProgressUpdate}
        onLoadProgressUpdate={this._onLoadProgressUpdate}
        onInfo={this._onInfo}
        onError={this._onError}
        onComplete={this._onComplete}
      />
    </View>;
  }
}

const styles = StyleSheet.create({
  container: {
    position: 'relative',
  },
  video: {
    position: 'absolute',
  },
  indicator: {
    position: 'absolute',
  },
});
var IJKPlayer = requireNativeComponent('RNEasyIjkplayerView', Video);

export default Video;
