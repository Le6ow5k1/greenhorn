(ns greenhorn.views
  (:use [hiccup core page])
  (:require [ring.util.anti-forgery :refer [anti-forgery-field]]))

(defn- head []
  [:head
   [:title "Greenhorn"]
   (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css")])

(defn- header-row []
  [:div.row
   [:div.col-md-12
    [:div.center-block {:style "width: 50%; text-align: center"}
     [:h1 "Greenhorn"]]]])

(defn index [projects]
  (html5
   (head)
   [:body
    [:div.container
     (header-row)
     [:div.row
      [:div.col-md-2]
      [:div.col-md-8
       [:div.pull-right
        [:a.btn.btn-default {:href "/projects/new"}
         [:span.glyphicon.glyphicon-plus {:aria-hidden "true"}]]]]
      [:div.col-md-2]]
     [:div.row
      [:div.col-md-2]
      [:div.col-md-8
       [:ul.center-block
        (for [{:keys [id name full_name]} projects]
          [:li
           [:span {:style "padding-right: 5%; font-size: 1.2em;"} name]
           [:span.text-muted {:style "padding-right: 5%;"} full_name]
           [:a {:href (str "/projects/" id "/edit")}
            [:span.glyphicon.glyphicon-pencil]]])]]
      [:div.col-md-2]]]]))

(defn- project-form [action method button-text {:keys [id name full_name gems_org]}]
  [:form {:action action :method method}
   (anti-forgery-field)
   [:div.form-group
    [:label {:for "name"} "Name"]
    [:input.form-control {:id "name" :name "name" :type "text" :placeholder "Name" :value name}]]
   [:div.form-group
    [:label {:for "full-name"} "Github Full Name"]
    [:input.form-control {:id "full-name" :name "full-name" :type "text" :placeholder "rails/rails" :value full_name}]]
   [:div.form-group
    [:label {:for "gems-org"} "Gems Organisation"]
    [:input.form-control {:id "gems-org" :name "gems-org" :type "text" :placeholder "rails" :value gems_org}]
    [:span.help-block "Organisation or user on Github where gems repositories can be found."]]
   [:button.btn.btn-default {:type "submit"} button-text]])

(defn add-project []
  (html5
   (head)
   [:body
    [:div.container
     (header-row)
     [:div.row
      [:div.col-md-2]
      [:div.col-md-8
       [:ul.center-block
        (project-form "/projects" "POST" "Create" {})]]
      [:div.col-md-2]]]]))

(defn edit-project [project]
  (html5
   (head)
   [:body
    [:div.container
     (header-row)
     [:div.row
      [:div.col-md-2]
      [:div.col-md-8
       [:ul.center-block
        (project-form (str "/projects/" (project :id)) "POST" "Update" project)]]
      [:div.col-md-2]]]]))
