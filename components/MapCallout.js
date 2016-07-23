'use strict'

import React, {Component, PropTypes} from 'react';
import {
  View,
  NativeMethodsMixin,
  requireNativeComponent,
  StyleSheet
} from 'react-native'


var MapCallout = React.createClass({
  mixins: [NativeMethodsMixin],

  propTypes: {
    ...View.propTypes,
    tooltip: PropTypes.bool,
    onPress: PropTypes.func,
  },

  getDefaultProps: function() {
    return {
      tooltip: false,
    };
  },

  render: function() {
    return <AIRMapCallout {...this.props} style={[styles.callout, this.props.style]} />;
  },
});

var styles = StyleSheet.create({
  callout: {
    position: 'absolute',
    //flex: 0,
    //backgroundColor: 'transparent',
  },
});

var AIRMapCallout = requireNativeComponent('AIRMapCallout', MapCallout);

module.exports = MapCallout;
