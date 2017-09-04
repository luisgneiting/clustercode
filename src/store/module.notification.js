import Notification from "../js/notifications"

export const mutation_types = {
    ADD_NOTIFICATION: "addNotification",
    CLEAR_NOTIFICATION: "clearNotification"
};

export const action_types = {
    ADD: "add",
    ADD_WITH_TIMEOUT: "addWithTimeout",
    ADD_INFO: "addInfo",
    ADD_WARNING: "addWarning",
    ADD_ERROR: "addError",
    ADD_SUCCESS: "addSuccess",
    CLEAR: "clear"
};

export const getters = {
    containsNotificationByKey(state, notificationKey) {
        if (!notificationKey || state.list.length === 0) return false;
        return !state.list.every(element => element.key !== notificationKey);
    },
    getAll(state) {
        return state.list;
    },
};

export const actions = {
    clear({commit, getters}, notification) {
        if (notification === undefined) {
            commit("clearAll");
        } else if (typeof notification === "string" && getters.containsNotificationByKey(notification)) {
            commit("clearNotificationsByKey", notification);
        } else {
            commit(mutation_types.CLEAR_NOTIFICATION, notification);
        }
    },
    add({commit, state, getters}, notification) {
        if (!notification || getters.containsNotificationByKey(state, notification.key)) return;
        commit(mutation_types.ADD_NOTIFICATION, notification);
    },
    addWithTimeout({commit}, notification) {
        if (!notification || getters.containsNotificationByKey(state, notification.key)) return;
        commit(mutation_types.ADD_NOTIFICATION, notification);
        setTimeout(() => {
            commit(mutation_types.CLEAR_NOTIFICATION, notification);
        }, 5000);
    },
    addInfo({commit}, message) {
        commit(mutation_types.ADD_NOTIFICATION, new Notification(Notification.LEVEL.INFO, message));
    },
    addSuccess({commit}, message) {
        commit(mutation_types.ADD_NOTIFICATION, new Notification(Notification.LEVEL.SUCCESS, message));
    },
    addWarning({commit}, message) {
        commit(mutation_types.ADD_NOTIFICATION, new Notification(Notification.LEVEL.WARN, message));
    },
    addError({commit}, message) {
        commit(mutation_types.ADD_NOTIFICATION, new Notification(Notification.LEVEL.ERROR, message));
    },
};

export const mutations = {
    addNotification(state, notification) {
        if (notification.key === null ||
            state.list.length === 0 ||
            !getters.containsNotificationByKey(state, notification.key)
        ) {
            state.list.push(notification);
        }
    },
    clearNotificationsByKey(state, key) {
        let arr = state.list;
        let notification;
        for (let i = arr.length - 1; i >= 0; i--)  {
            notification = arr[i];
            if (notification.key === key) {
                arr.splice(i, 1);
            }
        }
    },
    clearNotification(state, notification) {
        let index = state.list.indexOf(notification);
        if (index > -1) {
            state.list.splice(index, 1);
        }
    },
    clearAll(state) {
        state.list = [];
    },
};

const NotificationModule = {
    state: {
        list: []
    },
    actions,
    mutations,
    getters,
};

export default NotificationModule
