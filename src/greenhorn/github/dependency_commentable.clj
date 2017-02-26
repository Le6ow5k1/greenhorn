(ns greenhorn.github.dependency-commentable)

(defprotocol DependencyCommentable
  (added-comment [g] [g opts])
  (removed-comment [g] [g opts])
  (updated-comment [g] [g opts]))

(defprotocol DependencyDiffCommentable
  (to-comment [d] [d opts]))
