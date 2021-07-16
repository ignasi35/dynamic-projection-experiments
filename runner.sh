#!/usr/bin/env sh

for i in {1..100};
do
  for c in {1..1000};
  do
    grpcurl -d '{"cartId":"cartid-'${c}'", "itemId":"hoodie-item-'${i}'1", "quantity":5}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem  &
    grpcurl -d '{"cartId":"cartid-'${c}'", "itemId":"hoodie-item-'${i}'2", "quantity":5}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem  &
    grpcurl -d '{"cartId":"cartid-'${c}'", "itemId":"hoodie-item-'${i}'3", "quantity":5}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem  &
    grpcurl -d '{"cartId":"cartid-'${c}'", "itemId":"hoodie-item-'${i}'4", "quantity":5}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem  &
    grpcurl -d '{"cartId":"cartid-'${c}'", "itemId":"hoodie-item-'${i}'5", "quantity":5}' -plaintext 127.0.0.1:8101 shoppingcart.ShoppingCartService.AddItem
  done ;
done

