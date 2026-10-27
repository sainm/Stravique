(function () {
    function reloadOrders() {
        fetch("/orders.json", { credentials: "same-origin" });
    }

    window.addEventListener("load", reloadOrders);
}());
